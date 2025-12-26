package dev.appconnect

import android.app.Application
import android.os.Build
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.appconnect.core.HealthManager
import dev.appconnect.core.CrashReporter
import dev.appconnect.core.ClipboardCleanupWorker
import androidx.hilt.work.HiltWorkerFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AppConnectApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private lateinit var healthManager: HealthManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber
        Timber.plant(Timber.DebugTree())

        // Initialize crashReporter before it might be accessed
        crashReporter = CrashReporter(this)
        
        healthManager = HealthManager(this)

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
        // WorkManager.getInstance() will use our Configuration.Provider automatically
        scheduleCleanupWorker()
    }

    private fun scheduleCleanupWorker() {
        // WorkManager.getInstance() will use our Configuration.Provider
        // which provides the HiltWorkerFactory
        val workManager = WorkManager.getInstance(this)
        
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
            WORK_NAME_CLIPBOARD_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWork
        )

        Timber.d("Scheduled clipboard cleanup worker")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        const val WORK_NAME_CLIPBOARD_CLEANUP = "clipboard_cleanup"
        
        @Volatile
        var applicationExitReason: dev.appconnect.domain.model.ExitReason? = null

        @Volatile
        lateinit var crashReporter: CrashReporter
            private set
    }
}

