package dev.appconnect.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.appconnect.R
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.service.CopyActionReceiver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager: android.app.NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    companion object {
        const val CHANNEL_ID = "clipboard_sync_channel"
        private const val CHANNEL_NAME = "Clipboard Sync"
        const val ACTION_COPY_FROM_PC = "dev.appconnect.COPY_FROM_PC"
        const val EXTRA_CLIPBOARD_ID = "clipboard_id"
        
        // Service actions (moved from ClipboardSyncService)
        const val ACTION_START_SYNC = "dev.appconnect.START_SYNC"
        const val ACTION_STOP_SYNC = "dev.appconnect.STOP_SYNC"
        
        // Network timeouts
        const val CONNECTION_TIMEOUT_MS = 10000L
        const val SOCKET_TIMEOUT_MS = 10000L
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for clipboard synchronization"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Checks if the app has permission to post notifications on Android 13+ (API 33+).
     * @return true if permission is granted or not required (Android 12 and below), false otherwise
     */
    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true // Permission not required on Android 12 and below
    }

    fun showCopyNotification(clipboardItem: ClipboardItem, notificationId: Int) {
        // Check for POST_NOTIFICATIONS permission on Android 13+
        if (!hasNotificationPermission()) {
            Timber.w("POST_NOTIFICATIONS permission not granted, cannot show notification")
            return
        }
        
        val intent = Intent(context, CopyActionReceiver::class.java).apply {
            putExtra(EXTRA_CLIPBOARD_ID, clipboardItem.id)
            action = ACTION_COPY_FROM_PC
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_clipboard_from_pc))
            .setContentText(clipboardItem.previewText)
            .setSmallIcon(R.drawable.ic_clipboard_notification)
            .addAction(R.drawable.ic_clipboard_notification, context.getString(R.string.notification_action_copy), pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
        Timber.d("Showed copy notification for clipboard item: ${clipboardItem.id}")
    }

    fun showConnectionNotification(
        title: String,
        content: String,
        notificationId: Int
    ) {
        // Check for POST_NOTIFICATIONS permission on Android 13+
        if (!hasNotificationPermission()) {
            Timber.w("POST_NOTIFICATIONS permission not granted, cannot show notification")
            return
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_sync_tile)
            .setOngoing(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}

