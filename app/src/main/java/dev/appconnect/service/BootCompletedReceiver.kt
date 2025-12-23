package dev.appconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var context: Context

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

