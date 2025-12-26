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
import dev.appconnect.domain.model.Transport
import dev.appconnect.network.BluetoothManager
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
        setupConnectionStateMonitoring()
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
        // For Android 13+ (API 33+), foreground service requires POST_NOTIFICATIONS permission
        // Check if we have POST_NOTIFICATIONS permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (android.content.pm.PackageManager.PERMISSION_GRANTED != 
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                Timber.w("Cannot start foreground service without POST_NOTIFICATIONS permission")
                stopSelf()
                return
            }
        }
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
                // primaryClip is deprecated on Android 13+ - requires clipboard read permission
                // For Android 10+ background apps, we rely on the listener being registered
                // The listener itself indicates clipboard change, so we try to read it
                val clipData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // On Android 13+, primaryClip might return null for background apps
                    // but the listener still fires, so we check if we can read
                    try {
                        clipboardManager.primaryClip
                    } catch (e: SecurityException) {
                        Timber.w(e, "Cannot read clipboard in background on Android 13+")
                        return@launch
                    }
                } else {
                    @Suppress("DEPRECATION")
                    clipboardManager.primaryClip
                } ?: return@launch
                
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
        return hash.joinToString("") { "%02X".format(it) }
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
    
    private fun setupConnectionStateMonitoring() {
        // Monitor WebSocket connection state and update notification
        serviceScope.launch {
            webSocketClient.connectionState.collect { state ->
                Timber.d("WebSocket connection state changed: $state")
                updateNotificationForConnectionState(state)
            }
        }
        
        // Monitor Bluetooth connection state
        serviceScope.launch {
            bluetoothManager.connectionState.collect { state ->
                Timber.d("Bluetooth connection state changed: $state")
                // Update transport if Bluetooth becomes primary
                if (state == BluetoothManager.BluetoothConnectionState.Connected) {
                    currentTransport = Transport.BLUETOOTH
                    updateNotification(Transport.BLUETOOTH)
                }
            }
        }
    }
    
    private fun updateNotificationForConnectionState(state: WebSocketClient.ConnectionState) {
        if (!isServiceRunning()) return
        
        val notification = when (state) {
            WebSocketClient.ConnectionState.Connected -> {
                NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
                    .setContentTitle("Connected to PC")
                    .setContentText("Sync active via WebSocket")
                    .setSmallIcon(R.drawable.ic_sync_tile)
                    .setOngoing(true)
                    .build()
            }
            WebSocketClient.ConnectionState.Connecting -> {
                NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
                    .setContentTitle("Connecting to PC")
                    .setContentText("Establishing connection...")
                    .setSmallIcon(R.drawable.ic_sync_tile)
                    .setOngoing(true)
                    .build()
            }
            WebSocketClient.ConnectionState.Disconnecting -> {
                NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
                    .setContentTitle("Disconnecting from PC")
                    .setContentText("Closing connection...")
                    .setSmallIcon(R.drawable.ic_sync_tile)
                    .setOngoing(true)
                    .build()
            }
            WebSocketClient.ConnectionState.Disconnected -> {
                NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
                    .setContentTitle("Disconnected from PC")
                    .setContentText("Reconnecting...")
                    .setSmallIcon(R.drawable.ic_sync_tile)
                    .setOngoing(true)
                    .build()
            }
        }
        
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update notification for connection state")
        }
    }
    
    private fun isServiceRunning(): Boolean {
        // Simple check - service is running if it has been started
        return true
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

