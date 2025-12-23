package dev.appconnect.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dev.appconnect.domain.model.ExitReason
import timber.log.Timber

class HealthManager(private val context: Context) {
    @RequiresApi(Build.VERSION_CODES.R)
    fun checkExitReasons(): ExitReason? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null

        try {
            val exitReasons = activityManager.getHistoricalProcessExitReasons(
                context.packageName,
                0,      // pid (0 = current)
                1       // maxNum
            ) ?: return null

            if (exitReasons.isEmpty()) return null

            val exitInfo = exitReasons[0]
            return when (exitInfo.reason) {
                ActivityManager.ProcessExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> {
                    Timber.w("Previous exit reason: BATTERY_RESTRICTED")
                    ExitReason.BATTERY_RESTRICTED
                }
                ActivityManager.ProcessExitInfo.REASON_ANR -> {
                    Timber.w("Previous exit reason: ANR")
                    ExitReason.ANR
                }
                ActivityManager.ProcessExitInfo.REASON_CRASH -> {
                    Timber.w("Previous exit reason: CRASH")
                    ExitReason.CRASH
                }
                ActivityManager.ProcessExitInfo.REASON_PERMISSION_REVOKED -> {
                    Timber.w("Previous exit reason: PERMISSION_REVOKED")
                    ExitReason.PERMISSION_REVOKED
                }
                else -> {
                    Timber.d("Previous exit reason: ${exitInfo.reason}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check exit reasons")
            return null
        }
    }
}

