package dev.appconnect

import android.app.Application
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.appconnect.core.HealthManager
import dev.appconnect.core.CrashReporter
import dev.appconnect.core.ClipboardCleanupWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AppConnectApplication : Application() {

    private lateinit var healthManager: HealthManager
    private lateinit var workManager: WorkManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber
        Timber.plant(Timber.DebugTree())

        healthManager = HealthManager(this)
        crashReporter = CrashReporter(this)
        workManager = WorkManager.getInstance(this)

        // Check for previous exit reasons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val exitReason = healthManager.checkExitReasons()
            if (exitReason != null) {
                Timber.w("App previously exited with reason: $exitReason")
                // Store exit reason to show dialog in MainActivity
                applicationExitReason = exitReason
            }
        }

        // Schedule WorkManager cleanup
        scheduleCleanupWorker()
    }

    private fun scheduleCleanupWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val cleanupWork = PeriodicWorkRequestBuilder<ClipboardCleanupWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "clipboard_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork
        )

        Timber.d("Scheduled clipboard cleanup worker")
    }

    companion object {
        @Volatile
        var applicationExitReason: dev.appconnect.domain.model.ExitReason? = null
            private set

        @Volatile
        lateinit var crashReporter: CrashReporter
            private set
    }
}

