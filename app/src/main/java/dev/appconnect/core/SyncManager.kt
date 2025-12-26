package dev.appconnect.core

import android.app.ActivityManager
import android.content.Context
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.appconnect.core.encryption.EncryptedData
import dev.appconnect.core.encryption.EncryptionManager
import dev.appconnect.core.util.EncryptedDataSerializer
import dev.appconnect.data.repository.ClipboardRepository
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.domain.model.ContentType
import dev.appconnect.domain.model.Transport
import dev.appconnect.network.BluetoothManager
import dev.appconnect.network.WebSocketClient
import dev.appconnect.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ClipboardRepository,
    private val encryptionManager: EncryptionManager,
    private val webSocketClient: WebSocketClient,
    private val bluetoothManager: BluetoothManager,
    private val notificationManager: NotificationManager
) {
    companion object {
        const val DEFAULT_CLIPBOARD_TTL_MS = 24L * 60 * 60 * 1000 // 24 hours
        const val NOTIFICATION_DEBOUNCE_MS = 500L
        const val PREVIEW_TEXT_LENGTH = 50
        const val LOG_MESSAGE_PREVIEW_LENGTH = 50
        const val NOTIFICATION_ID_CLIPBOARD_COPY = 1001
    }
    
    private var pendingNotificationJob: Job? = null
    private var pendingClipboardItem: ClipboardItem? = null

    private var currentTransport: Transport = Transport.WEBSOCKET

    fun setCurrentTransport(transport: Transport) {
        currentTransport = transport
        Timber.d("Transport changed to: $transport")
    }

    fun getCurrentTransport(): Transport = currentTransport

    suspend fun syncClipboard(clipboardItem: ClipboardItem): kotlin.Result<Boolean> {
        // Trap A: Images can only sync via WebSocket
        if (clipboardItem.contentType == ContentType.IMAGE && currentTransport == Transport.BLUETOOTH) {
            // Show toast and abort
            Toast.makeText(
                context,
                context.getString(dev.appconnect.R.string.error_wifi_required_for_image_sync),
                Toast.LENGTH_LONG
            ).show()
            Timber.w("Image sync attempted over Bluetooth - blocked")
            return kotlin.Result.failure(Exception("Image sync requires WebSocket transport"))
        }

        // Save to repository first (queue if connection is down)
        repository.saveClipboardItem(clipboardItem)

        // Text can sync via either transport
        if (clipboardItem.contentType == ContentType.TEXT) {
            return when (currentTransport) {
                Transport.WEBSOCKET -> {
                    // Use session encryption if available, otherwise fall back to local encryption
                    val sessionEncryption = webSocketClient.getSessionEncryption()
                    val encrypted = if (sessionEncryption != null) {
                        val json = serializeClipboardItem(clipboardItem)
                        sessionEncryption.encrypt(json)
                    } else {
                        Timber.w("Session encryption not available, using local encryption")
                        encryptClipboardItem(clipboardItem)
                    }
                    
                    val message = serializeForTransmission(encrypted)
                    if (webSocketClient.send(message)) {
                        repository.markAsSynced(clipboardItem.id)
                        kotlin.Result.success(true)
                    } else {
                        kotlin.Result.failure(Exception("WebSocket send failed"))
                    }
                }
                Transport.BLUETOOTH -> {
                    val encrypted = encryptClipboardItem(clipboardItem)
                    val message = serializeForTransmission(encrypted)
                    bluetoothManager.send(message).fold(
                        onSuccess = {
                            repository.markAsSynced(clipboardItem.id)
                            kotlin.Result.success(true)
                        },
                        onFailure = { kotlin.Result.failure(it) }
                    )
                }
                else -> kotlin.Result.failure(Exception("No available transport"))
            }
        }

        // Images: WebSocket only
        if (clipboardItem.contentType == ContentType.IMAGE) {
            if (currentTransport != Transport.WEBSOCKET) {
                Toast.makeText(
                    context,
                    context.getString(dev.appconnect.R.string.error_wifi_required_for_image_sync_short),
                    Toast.LENGTH_LONG
                ).show()
                return kotlin.Result.failure(Exception("Wi-Fi required for image sync"))
            }
            val encrypted = encryptClipboardItem(clipboardItem)
            val message = serializeForTransmission(encrypted)
            if (webSocketClient.send(message)) {
                repository.markAsSynced(clipboardItem.id)
                kotlin.Result.success(true)
            } else {
                kotlin.Result.failure(Exception("WebSocket send failed"))
            }
        }

        return kotlin.Result.failure(Exception("Unsupported content type"))
    }

    // Use application scope for handling incoming clipboard data
    // This ensures coroutines are managed properly and don't leak
    private val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    fun handleIncomingClipboard(data: EncryptedData) {
        handlerScope.launch {
            try {
                // Use session encryption if available, otherwise fall back to local encryption
                val sessionEncryption = webSocketClient.getSessionEncryption()
                val decrypted = if (sessionEncryption != null) {
                    sessionEncryption.decrypt(data)
                } else {
                    Timber.w("Session encryption not available, using local encryption")
                    encryptionManager.decrypt(data)
                }
                
                // Parse the decrypted JSON to get ClipboardItem
                val clipboardItem = parseFromTransmission(decrypted)
                repository.saveClipboardItem(clipboardItem)

                if (isAppInForeground()) {
                    // Direct write - app has focus
                    writeToClipboard(clipboardItem)
                } else {
                    // Background: debounce and show notification with action
                    debounceNotification(clipboardItem)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle incoming clipboard")
            }
        }
    }

    private fun debounceNotification(clipboardItem: ClipboardItem) {
        // Cancel any pending notification job
        pendingNotificationJob?.cancel()

        // Store the latest clipboard item
        pendingClipboardItem = clipboardItem

        // Schedule notification after debounce delay
        pendingNotificationJob = handlerScope.launch {
            delay(NOTIFICATION_DEBOUNCE_MS)
            pendingClipboardItem?.let { item ->
                notificationManager.showCopyNotification(item, NOTIFICATION_ID_CLIPBOARD_COPY)
                pendingClipboardItem = null
            }
        }
    }

    private fun isAppInForeground(): Boolean {
        // runningAppProcesses is deprecated - use simpler approach for our use case
        // Since this is called from a service context, we check if any activity is in foreground
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false

        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Use getAppTasks() for more reliable foreground detection
                val appTasks = activityManager.appTasks
                appTasks?.isNotEmpty() == true
            } else {
                // Fallback: runningAppProcesses is deprecated but still works on older APIs
                @Suppress("DEPRECATION")
                val appProcesses = activityManager.runningAppProcesses
                val packageName = context.packageName
                appProcesses?.any { process ->
                    process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                            && process.processName == packageName
                } == true
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to check if app is in foreground")
            false
        }
    }

    private fun writeToClipboard(clipboardItem: ClipboardItem) {
        // This will be implemented by the service that calls SyncManager
        // For now, just log
        Timber.d("Writing to clipboard: ${clipboardItem.content.take(LOG_MESSAGE_PREVIEW_LENGTH)}")
    }

    private fun encryptClipboardItem(item: ClipboardItem): EncryptedData {
        val json = serializeClipboardItem(item)
        return encryptionManager.encrypt(json)
    }

    private fun serializeClipboardItem(item: ClipboardItem): String {
        return Json.encodeToString(ClipboardItem.serializer(), item)
    }

    private fun parseFromTransmission(json: String): ClipboardItem {
        return Json.decodeFromString(ClipboardItem.serializer(), json)
    }

    private fun serializeForTransmission(encrypted: EncryptedData): String {
        return EncryptedDataSerializer.serialize(encrypted)
    }
}

