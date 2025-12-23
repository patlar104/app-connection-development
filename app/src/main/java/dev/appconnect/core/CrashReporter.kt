package dev.appconnect.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

class CrashReporter(private val context: Context) {
    @RequiresApi(Build.VERSION_CODES.R)
    fun logExitInfo(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null

        return try {
            val exitReasons = activityManager.getHistoricalProcessExitReasons(
                context.packageName,
                0,
                1
            ) ?: return null

            if (exitReasons.isEmpty()) return null

            val exitInfo = exitReasons[0]
            val log = buildString {
                appendLine("Exit Reason: ${exitInfo.reason}")
                appendLine("Timestamp: ${exitInfo.timestamp}")
                exitInfo.description?.let {
                    appendLine("Description: $it")
                }
                if (exitInfo.status != null) {
                    appendLine("Status: ${exitInfo.status}")
                }
            }

            Timber.d("Exit info logged: $log")
            log
        } catch (e: Exception) {
            Timber.e(e, "Failed to log exit info")
            null
        }
    }
}

