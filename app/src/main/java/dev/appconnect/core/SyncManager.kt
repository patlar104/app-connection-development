package dev.appconnect.core

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
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
    private var lastWrittenHash: String? = null
    private var lastWrittenTime: Long = 0

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
                        // Report success
                        reportClipboardSyncResult(true, clipboardItem.id, "Sent via WebSocket")
                        kotlin.Result.success(true)
                    } else {
                        // Report failure
                        reportError("send_failed", "WebSocket send failed for clipboard item ${clipboardItem.id}", null)
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
                    // Report success
                    reportClipboardSyncResult(true, clipboardItem.id, "Written to clipboard")
                } else {
                    // Background: debounce and show notification with action
                    debounceNotification(clipboardItem)
                    // Report success (notification shown)
                    reportClipboardSyncResult(true, clipboardItem.id, "Notification shown")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle incoming clipboard")
                // Report error
                reportError("decryption_failed", "Failed to handle incoming clipboard: ${e.message}", e)
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
        try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: run {
                    Timber.e("ClipboardManager not available")
                    return
                }
            
            // Create ClipData with the clipboard content
            val clipData = ClipData.newPlainText(
                context.getString(R.string.service_pc_clipboard_label),
                clipboardItem.content
            )
            
            // Set the clipboard content
            clipboardManager.setPrimaryClip(clipData)
            
            // Track what we just wrote to prevent loop (ignore this hash for next 2 seconds)
            lastWrittenHash = clipboardItem.hash
            lastWrittenTime = System.currentTimeMillis()
            
            Timber.d("Successfully wrote to clipboard: ${clipboardItem.content.take(LOG_MESSAGE_PREVIEW_LENGTH)}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write to clipboard: ${clipboardItem.content.take(LOG_MESSAGE_PREVIEW_LENGTH)}")
            // Report error to PC
            reportError("clipboard_write_failed", "Failed to write clipboard on Android: ${e.message}", e)
        }
    }
    
    /**
     * Check if a clipboard item should be ignored to prevent sync loops.
     * Returns true if the item matches content we just wrote to clipboard.
     */
    fun shouldIgnoreClipboardItem(hash: String): Boolean {
        val now = System.currentTimeMillis()
        // Ignore items with the same hash as what we wrote in the last 2 seconds
        return lastWrittenHash == hash && (now - lastWrittenTime) < 2000
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
    
    /**
     * Report error to PC server.
     */
    private fun reportError(errorType: String, message: String, exception: Exception?) {
        try {
            val details = mutableMapOf<String, Any>()
            exception?.let {
                details["exception_type"] = it.javaClass.simpleName
                details["exception_message"] = it.message ?: "Unknown error"
            }
            
            webSocketClient.sendErrorReport(errorType, message, details)
        } catch (e: Exception) {
            Timber.e(e, "Failed to report error to PC")
        }
    }
    
    /**
     * Report clipboard sync result to PC server.
     */
    private fun reportClipboardSyncResult(success: Boolean, clipboardId: String, message: String) {
        try {
            val result = mapOf(
                "success" to success,
                "clipboard_id" to clipboardId,
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Send as error report with type clipboard_sync_result
            webSocketClient.sendErrorReport(
                if (success) "clipboard_sync_success" else "clipboard_sync_failed",
                message,
                result
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report clipboard sync result")
        }
    }
    
    /**
     * Report connection status to PC server.
     */
    fun reportConnectionStatus(status: String) {
        try {
            webSocketClient.sendConnectionStatus(status)
        } catch (e: Exception) {
            Timber.e(e, "Failed to report connection status")
        }
    }
}

