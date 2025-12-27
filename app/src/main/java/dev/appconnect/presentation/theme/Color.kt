package dev.appconnect.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Custom colors (fallback when dynamic color not available)
val AppPrimary = Color(0xFF6750A4)
val AppOnPrimary = Color(0xFFFFFFFF)
val AppPrimaryContainer = Color(0xFFEADDFF)
val AppOnPrimaryContainer = Color(0xFF21005D)

// Connection status colors
val ConnectionStatusConnected = Color(0xFF4CAF50)
val ConnectionStatusConnecting = Color(0xFFFFC107)
val ConnectionStatusDisconnecting = Color(0xFFFF9800)
val ConnectionStatusDisconnected = Color(0xFFF44336)
val QRScannerOverlayWhite = Color.White

@Composable
fun appColorScheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true  // Android 12+ dynamic color
): ColorScheme {
    val context = LocalContext.current
    
    return when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> createDarkColorScheme()
        else -> createLightColorScheme()
    }
}

private fun createDarkColorScheme() = darkColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    primaryContainer = AppPrimaryContainer,
    onPrimaryContainer = AppOnPrimaryContainer
)

private fun createLightColorScheme() = lightColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    primaryContainer = AppPrimaryContainer,
    onPrimaryContainer = AppOnPrimaryContainer
)

