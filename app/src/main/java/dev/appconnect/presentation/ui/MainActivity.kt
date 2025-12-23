package dev.appconnect.presentation.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.appconnect.AppConnectApplication
import dev.appconnect.domain.model.ExitReason
import dev.appconnect.presentation.theme.AppConnectTheme
import dev.appconnect.presentation.viewmodel.MainViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for exit reason and show dialog if needed
        checkExitReason()

        setContent {
            AppConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun checkExitReason() {
        val exitReason = AppConnectApplication.applicationExitReason ?: return

        when (exitReason) {
            ExitReason.BATTERY_RESTRICTED -> {
                // Show battery optimization dialog
                showBatteryOptimizationDialog()
            }
            ExitReason.PERMISSION_REVOKED -> {
                // Show permission dialog
                showPermissionDialog()
            }
            ExitReason.CRASH, ExitReason.ANR -> {
                // Show crash log dialog
                showCrashLogDialog()
            }
        }

        // Clear exit reason after showing dialog
        AppConnectApplication.applicationExitReason = null
    }

    private fun showBatteryOptimizationDialog() {
        // Implementation would show dialog with button to open battery optimization settings
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        )
        startActivity(intent)
    }

    private fun showPermissionDialog() {
        // Implementation would show dialog with button to open app settings
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        ).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun showCrashLogDialog() {
        // Implementation would show dialog with crash log and copy/restart buttons
        val crashLog = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AppConnectApplication.crashReporter.logExitInfo()
        } else {
            null
        }
        // Show dialog with crashLog
    }
}

