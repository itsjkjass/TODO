package com.pacemate.app.ui.theme

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

private val Light = lightColorScheme(
    primary = Color(0xFF2E6B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4EFDF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A6A1F),
    secondaryContainer = Color(0xFFFFE08F),
    tertiary = Color(0xFF4A6280),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF98D3C3),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF105245),
    onPrimaryContainer = Color(0xFFB4EFDF),
    secondary = Color(0xFFE9C46A),
    secondaryContainer = Color(0xFF5C4A12),
    tertiary = Color(0xFFB1C8EA),
)

/** Load colours used for the day-load bars. */
object LoadColors {
    val Calm = Color(0xFF4CAF7A)
    val Busy = Color(0xFFE0A030)
    val Heavy = Color(0xFFD9534F)
}

@Composable
fun PaceMateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
