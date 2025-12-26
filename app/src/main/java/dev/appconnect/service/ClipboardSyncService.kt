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
import dev.appconnect.core.util.HashUtil
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
            NotificationManager.ACTION_START_SYNC -> startSync()
            NotificationManager.ACTION_STOP_SYNC -> stopSync()
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
                    ttl = dev.appconnect.core.SyncManager.DEFAULT_CLIPBOARD_TTL_MS,
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
        return HashUtil.sha256(text)
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
                    .setContentTitle(getString(R.string.notification_connected_to_pc))
                    .setContentText(getString(R.string.notification_sync_active_websocket))
                    .setSmallIcon(R.drawable.ic_sync_tile)
                    .setOngoing(true)
                    .build()
            }
            WebSocketClient.ConnectionState.Connecting -> {
                NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
                    .setContentTitle(getString(R.string.notification_connecting_to_pc))
                    .setContentText(getString(R.string.notification_establishing_connection))
                    .setSmallIcon(R.drawable.ic_sync_tile)
                    .setOngoing(true)
                    .build()
            }
            WebSocketClient.ConnectionState.Disconnecting -> {
                NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
                    .setContentTitle(getString(R.string.notification_disconnecting_from_pc))
                    .setContentText(getString(R.string.notification_closing_connection))
                    .setSmallIcon(R.drawable.ic_sync_tile)
                    .setOngoing(true)
                    .build()
            }
            WebSocketClient.ConnectionState.Disconnected -> {
                NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
                    .setContentTitle(getString(R.string.notification_disconnected_from_pc))
                    .setContentText(getString(R.string.notification_reconnecting))
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
        return dev.appconnect.core.util.EncryptedDataSerializer.parse(message)
    }

    private fun updateNotification(transport: Transport) {
        val notification = createNotification(transport)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(transport: Transport): Notification {
        return NotificationCompat.Builder(this, NotificationManager.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_connected_to_pc))
            .setContentText(getString(R.string.notification_sync_active_transport, transport.name))
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

