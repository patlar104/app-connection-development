package dev.appconnect.service

import android.app.Notification
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.appconnect.R
import dev.appconnect.core.NotificationManager
import dev.appconnect.core.SyncManager
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.domain.model.ContentType
import dev.appconnect.network.BluetoothManager
import dev.appconnect.network.Transport
import dev.appconnect.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardSyncService : Service() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var webSocketClient: WebSocketClient

    @Inject
    lateinit var bluetoothManager: BluetoothManager

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var repository: dev.appconnect.data.repository.ClipboardRepository

    private var currentTransport: Transport = Transport.WEBSOCKET
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_START_SYNC = "dev.appconnect.START_SYNC"
        const val ACTION_STOP_SYNC = "dev.appconnect.STOP_SYNC"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("ClipboardSyncService created")
        setupClipboardListener()
        setupWebSocketListener()
        setupBluetoothListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SYNC -> startSync()
            ACTION_STOP_SYNC -> stopSync()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSync() {
        Timber.d("Starting clipboard sync")
        startForeground(NOTIFICATION_ID, createNotification(Transport.WEBSOCKET))
    }

    private fun stopSync() {
        Timber.d("Stopping clipboard sync")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setupClipboardListener() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChange(clipboardManager)
        }
        clipboardManager.addPrimaryClipChangedListener(clipboardListener!!)
        Timber.d("Clipboard listener registered")
    }

    private fun handleClipboardChange(clipboardManager: ClipboardManager) {
        serviceScope.launch {
            try {
                val clipData = clipboardManager.primaryClip ?: return@launch
                if (clipData.itemCount == 0) return@launch

                val item = clipData.getItemAt(0)
                val text = item.coerceToText(applicationContext).toString()
                if (text.isBlank()) return@launch

                val clipboardItem = ClipboardItem(
                    id = java.util.UUID.randomUUID().toString(),
                    content = text,
                    contentType = ContentType.TEXT,
                    timestamp = System.currentTimeMillis(),
                    ttl = 24 * 60 * 60 * 1000L, // 24 hours
                    synced = false,
                    sourceDeviceId = null,
                    hash = calculateHash(text)
                )

                syncManager.syncClipboard(clipboardItem)
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle clipboard change")
            }
        }
    }

    private fun calculateHash(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun setupWebSocketListener() {
        webSocketClient.setMessageListener { message ->
            try {
                val encryptedData = parseEncryptedMessage(message)
                syncManager.handleIncomingClipboard(encryptedData)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse WebSocket message")
            }
        }
    }

    private fun setupBluetoothListener() {
        bluetoothManager.setMessageListener { message ->
            try {
                val encryptedData = parseEncryptedMessage(message)
                syncManager.handleIncomingClipboard(encryptedData)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse Bluetooth message")
            }
        }
    }

    private fun parseEncryptedMessage(message: String): dev.appconnect.core.encryption.EncryptedData {
        val parts = message.split("|")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid message format")
        }
        val iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
        val encryptedBytes = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        return dev.appconnect.core.encryption.EncryptedData(
            encryptedBytes = encryptedBytes,
            iv = iv
        )
    }

    private fun handleWebSocketFailure() {
        Timber.w("WebSocket connection lost, attempting Bluetooth fallback")
        syncManager.setCurrentTransport(Transport.BLUETOOTH)

        // Attempt Bluetooth connection
        serviceScope.launch {
            // Get paired device from repository
            val pairedDevices = repository.getPairedDevices()
            val device = pairedDevices.firstOrNull() ?: run {
                Timber.e("No paired devices available for Bluetooth fallback")
                stopSelf()
                return@launch
            }
            
            // Extract Bluetooth address from device info
            val bluetoothAddress = device.bluetoothAddress ?: run {
                Timber.e("Device has no Bluetooth address")
                stopSelf()
                return@launch
            }
            
            val result = bluetoothManager.connect(bluetoothAddress)
            result.fold(
                onSuccess = {
                    currentTransport = Transport.BLUETOOTH
                    updateNotification(Transport.BLUETOOTH)
                },
                onFailure = { error ->
                    Timber.e("Both transports failed: ${error.message}")
                    stopSelf()
                }
            )
        }
    }

    private fun updateNotification(transport: Transport) {
        val notification = createNotification(transport)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(transport: Transport): Notification {
        return NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
            .setContentTitle("Connected to PC")
            .setContentText("Sync active via ${transport.name}")
            .setSmallIcon(R.drawable.ic_sync_tile)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardListener?.let {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.removePrimaryClipChangedListener(it)
        }
        serviceScope.cancel()
        webSocketClient.disconnect()
        bluetoothManager.disconnect()
        Timber.d("ClipboardSyncService destroyed")
    }
}

