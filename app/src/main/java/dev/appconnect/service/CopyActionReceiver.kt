package dev.appconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import dagger.hilt.android.EntryPointAccessors
import dev.appconnect.AppConnectApplication
import dev.appconnect.R
import dev.appconnect.core.NotificationManager
import dev.appconnect.di.RepositoryEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class CopyActionReceiver : BroadcastReceiver() {
    
    // Use a single instance executor for BroadcastReceiver to avoid leaks
    // BroadcastReceiver lifecycle is short-lived, so we need to handle cleanup
    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_COPY_FROM_PC) {
            return
        }

        val clipboardId = intent.getStringExtra(NotificationManager.EXTRA_CLIPBOARD_ID) ?: return

        // Get repository via EntryPointAccessors for BroadcastReceiver
        val application = context.applicationContext as? AppConnectApplication ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            application,
            RepositoryEntryPoint::class.java
        )
        val repository = entryPoint.clipboardRepository()

        receiverScope.launch {
            val item = repository.getClipboardItem(clipboardId) ?: run {
                Timber.w("Clipboard item not found: $clipboardId")
                return@launch
            }

            // Now we have foreground focus from notification tap
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.service_pc_clipboard_label), item.content))

            Timber.d("Copied clipboard item to system clipboard: ${item.id}")
        }
    }
}
