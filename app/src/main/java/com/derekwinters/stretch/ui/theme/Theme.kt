package com.derekwinters.stretch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D6B),
    secondary = Color(0xFF4A635D),
    tertiary = Color(0xFF8A5A2B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD8C1),
    secondary = Color(0xFFB1CCC4),
    tertiary = Color(0xFFF0BD8B),
)

/**
 * The app theme. [darkTheme] and [dynamicColor] default to the system's dark mode and to
 * wallpaper colours where Android has them (12+); the store screenshots pass both explicitly to
 * get the fixed schemes below (STORE-SHOT-006).
 */
@Composable
fun StretchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = darkTheme
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
