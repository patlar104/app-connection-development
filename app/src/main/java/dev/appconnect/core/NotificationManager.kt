package dev.appconnect.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.appconnect.R
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.service.CopyActionReceiver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    private val context: Context
) {
    private val notificationManager: android.app.NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    companion object {
        private const val CHANNEL_ID = "clipboard_sync_channel"
        private const val CHANNEL_NAME = "Clipboard Sync"
        const val ACTION_COPY_FROM_PC = "dev.appconnect.COPY_FROM_PC"
        const val EXTRA_CLIPBOARD_ID = "clipboard_id"
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

    fun showCopyNotification(clipboardItem: ClipboardItem, notificationId: Int) {
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
            .setContentTitle("Clipboard from PC")
            .setContentText(clipboardItem.previewText)
            .setSmallIcon(android.R.drawable.ic_menu_edit) // Placeholder icon
            .addAction(android.R.drawable.ic_menu_edit, "Copy", pendingIntent)
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_share) // Placeholder icon
            .setOngoing(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}

