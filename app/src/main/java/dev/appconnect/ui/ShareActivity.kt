package dev.appconnect.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import dev.appconnect.core.SyncManager
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
                if (intent.type == "text/plain") {
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
                ttl = 24 * 60 * 60 * 1000L, // 24 hours
                synced = false,
                sourceDeviceId = null,
                hash = calculateHash(text)
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

    private fun calculateHash(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray())
        return hash.joinToString("") { "%02X".format(it) }
    }
}

