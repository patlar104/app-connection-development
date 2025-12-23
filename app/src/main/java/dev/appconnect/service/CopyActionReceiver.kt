package dev.appconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import dagger.hilt.android.AndroidEntryPoint
import dev.appconnect.core.NotificationManager
import dev.appconnect.data.repository.ClipboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CopyActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ClipboardRepository

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_COPY_FROM_PC) {
            return
        }

        val clipboardId = intent.getStringExtra(NotificationManager.EXTRA_CLIPBOARD_ID) ?: return

        receiverScope.launch {
            val item = repository.getClipboardItem(clipboardId) ?: run {
                Timber.w("Clipboard item not found: $clipboardId")
                return@launch
            }

            // Now we have foreground focus from notification tap
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PC Clipboard", item.content))

            Timber.d("Copied clipboard item to system clipboard: ${item.id}")
        }
    }
}

