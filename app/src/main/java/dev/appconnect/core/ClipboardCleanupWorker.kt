package dev.appconnect.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.appconnect.data.local.database.AppDatabase
import timber.log.Timber
import javax.inject.Inject

@HiltWorker
class ClipboardCleanupWorker @Inject constructor(
    @ApplicationContext context: Context,
    params: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        return try {
            val now = System.currentTimeMillis()
            val dao = database.clipboardItemDao()
            val expiredItems = dao.getExpiredItems(now)

            dao.deleteExpiredItems(now)

            Timber.d("Cleaned up ${expiredItems.size} expired clipboard items")
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup clipboard items")
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}

