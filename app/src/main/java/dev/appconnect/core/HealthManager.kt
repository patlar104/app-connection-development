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
            )

            if (exitReasons.isEmpty()) return null

            val exitInfo = exitReasons[0]
            // Use integer constants directly (API 30+)
            // REASON_EXCESSIVE_RESOURCE_USAGE = 1
            // REASON_ANR = 2
            // REASON_CRASH = 3
            // REASON_PERMISSION_REVOKED = 4
            // REASON_APP_EXIT = 10 (normal app termination, not an error)
            return when (exitInfo.reason) {
                1 -> { // REASON_EXCESSIVE_RESOURCE_USAGE
                    Timber.w("Previous exit reason: BATTERY_RESTRICTED")
                    ExitReason.BATTERY_RESTRICTED
                }
                2 -> { // REASON_ANR
                    Timber.w("Previous exit reason: ANR")
                    ExitReason.ANR
                }
                3 -> { // REASON_CRASH
                    Timber.w("Previous exit reason: CRASH")
                    ExitReason.CRASH
                }
                4 -> { // REASON_PERMISSION_REVOKED
                    Timber.w("Previous exit reason: PERMISSION_REVOKED")
                    ExitReason.PERMISSION_REVOKED
                }
                10 -> { // REASON_APP_EXIT (normal termination - not an error)
                    Timber.d("Previous exit reason: APP_EXIT (normal termination)")
                    null // Normal exit, no user action needed
                }
                else -> {
                    Timber.d("Previous exit reason: ${exitInfo.reason} (not a critical error)")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check exit reasons")
            return null
        }
    }
}

