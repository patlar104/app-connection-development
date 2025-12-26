package dev.appconnect.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.appconnect.core.SyncManager
import dev.appconnect.core.util.HashUtil
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.domain.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {
    companion object {
        const val MIME_TYPE_TEXT_PLAIN = "text/plain"
    }

    @Inject
    lateinit var syncManager: SyncManager

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup scope when activity is destroyed
        activityScope.cancel()
    }

    private fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == MIME_TYPE_TEXT_PLAIN) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    sharedText?.let { text ->
                        syncSharedText(text)
                    }
                }
            }
        }
    }

    private fun syncSharedText(text: String) {
        activityScope.launch {
            val clipboardItem = ClipboardItem(
                id = UUID.randomUUID().toString(),
                content = text,
                contentType = ContentType.TEXT,
                timestamp = System.currentTimeMillis(),
                ttl = SyncManager.DEFAULT_CLIPBOARD_TTL_MS,
                synced = false,
                sourceDeviceId = null,
                hash = HashUtil.sha256(text)
            )

            val result = syncManager.syncClipboard(clipboardItem)
            result.fold(
                onSuccess = {
                    Timber.d("Shared text synced successfully")
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to sync shared text")
                }
            )
        }
    }
}

