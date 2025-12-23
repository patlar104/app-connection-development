package dev.appconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed - starting clipboard sync service")
            val serviceIntent = Intent(context, ClipboardSyncService::class.java).apply {
                action = ClipboardSyncService.ACTION_START_SYNC
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
