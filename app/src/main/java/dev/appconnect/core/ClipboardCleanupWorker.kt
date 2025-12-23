package dev.appconnect.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.appconnect.data.local.database.dao.ClipboardItemDao
import timber.log.Timber
import javax.inject.Inject

@HiltWorker
class ClipboardCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val clipboardItemDao: ClipboardItemDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val expiredItems = clipboardItemDao.getExpiredItems(now)

            clipboardItemDao.deleteExpiredItems(now)

            Timber.d("Cleaned up ${expiredItems.size} expired clipboard items")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup clipboard items")
            Result.retry()
        }
    }
}

