package dev.appconnect.core

import android.app.ActivityManager
import android.content.Context
import android.widget.Toast
import dev.appconnect.core.encryption.EncryptedData
import dev.appconnect.core.encryption.EncryptionManager
import dev.appconnect.data.repository.ClipboardRepository
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.domain.model.ContentType
import dev.appconnect.network.BluetoothManager
import dev.appconnect.network.Transport
import dev.appconnect.network.WebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val context: Context,
    private val repository: ClipboardRepository,
    private val encryptionManager: EncryptionManager,
    private val webSocketClient: WebSocketClient,
    private val bluetoothManager: BluetoothManager,
    private val notificationManager: NotificationManager
) {
    private var pendingNotificationJob: Job? = null
    private var pendingClipboardItem: ClipboardItem? = null
    private val notificationId = 1001 // Single notification ID for all clipboard notifications

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
                "Connect to Wi-Fi to sync image",
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
                    val encrypted = encryptClipboardItem(clipboardItem)
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
                    "Wi-Fi required for image sync",
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

    fun handleIncomingClipboard(data: EncryptedData) {
        try {
            val decrypted = encryptionManager.decrypt(data)
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

    private fun debounceNotification(clipboardItem: ClipboardItem) {
        // Cancel any pending notification job
        pendingNotificationJob?.cancel()

        // Store the latest clipboard item
        pendingClipboardItem = clipboardItem

        // Schedule notification after 500ms debounce
        pendingNotificationJob = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            pendingClipboardItem?.let { item ->
                notificationManager.showCopyNotification(item, notificationId)
                pendingClipboardItem = null
            }
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false

        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName

        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName
            ) {
                return true
            }
        }
        return false
    }

    private fun writeToClipboard(clipboardItem: ClipboardItem) {
        // This will be implemented by the service that calls SyncManager
        // For now, just log
        Timber.d("Writing to clipboard: ${clipboardItem.content.take(50)}")
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
        val ivBase64 = android.util.Base64.encodeToString(encrypted.iv, android.util.Base64.NO_WRAP)
        val encryptedBase64 = android.util.Base64.encodeToString(
            encrypted.encryptedBytes,
            android.util.Base64.NO_WRAP
        )
        return "$ivBase64|$encryptedBase64"
    }
}

