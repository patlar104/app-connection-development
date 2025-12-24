package dev.appconnect.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // Enable dynamic color on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = appColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor)
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use WindowInsetsController instead of deprecated statusBarColor
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            // statusBarColor is deprecated, handled by WindowInsetsController
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.surface.toArgb()
            }
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

