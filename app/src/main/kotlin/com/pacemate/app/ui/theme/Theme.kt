package com.pacemate.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * PaceMate design system
 * ----------------------
 * Brand: calm teal for focus, warm orange for time and energy, indigo for deep work.
 * Every colour is a semantic token with separate light and dark values. Body text
 * has at least 4.5:1 contrast on its surface in both themes. Screens must read
 * colours from MaterialTheme.colorScheme / PaceTheme.colors, never raw hex.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF5EC),
    onPrimaryContainer = Color(0xFF032F2B),
    inversePrimary = Color(0xFF5EEAD4),
    secondary = Color(0xFFC2410C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEAD5),
    onSecondaryContainer = Color(0xFF431407),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3E6FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE6E9EE),
    onSurfaceVariant = Color(0xFF475467),
    surfaceTint = Color(0xFF0F766E),
    inverseSurface = Color(0xFF1A212B),
    inverseOnSurface = Color(0xFFF1F4F8),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF55160C),
    outline = Color(0xFF8792A2),
    outlineVariant = Color(0xFFDDE2E8),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE2E6EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F5),
    surfaceContainerHigh = Color(0xFFE9ECF0),
    surfaceContainerHighest = Color(0xFFE2E6EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD8C4),
    onPrimary = Color(0xFF00332D),
    primaryContainer = Color(0xFF0D4D46),
    onPrimaryContainer = Color(0xFFCCF5EC),
    inversePrimary = Color(0xFF0F766E),
    secondary = Color(0xFFFF9F5A),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF6B2A0A),
    onSecondaryContainer = Color(0xFFFFEAD5),
    tertiary = Color(0xFFAAB4FF),
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF34318A),
    onTertiaryContainer = Color(0xFFE3E6FF),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF232B35),
    onSurfaceVariant = Color(0xFFA9B3C1),
    surfaceTint = Color(0xFF4FD8C4),
    inverseSurface = Color(0xFFE8ECF1),
    inverseOnSurface = Color(0xFF151B22),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF55160C),
    errorContainer = Color(0xFF7A271A),
    onErrorContainer = Color(0xFFFEE4E2),
    outline = Color(0xFF6E7987),
    outlineVariant = Color(0xFF2A333E),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A323C),
    surfaceDim = Color(0xFF0B0F14),
    surfaceContainerLowest = Color(0xFF070A0E),
    surfaceContainerLow = Color(0xFF12171E),
    surfaceContainer = Color(0xFF161C24),
    surfaceContainerHigh = Color(0xFF1D242D),
    surfaceContainerHighest = Color(0xFF252D37),
)

/** Status colours that Material doesn't name: load levels and outcomes. */
@Immutable
data class PaceColors(
    val calm: Color,
    val busy: Color,
    val heavy: Color,
    val success: Color,
    val onSuccess: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val track: Color,
)

private val LightPace = PaceColors(
    calm = Color(0xFF0F766E),
    busy = Color(0xFFB54708),
    heavy = Color(0xFFB42318),
    success = Color(0xFF067647),
    onSuccess = Color.White,
    warningContainer = Color(0xFFFEF0C7),
    onWarningContainer = Color(0xFF4E1D09),
    track = Color(0xFFE4E8ED),
)

private val DarkPace = PaceColors(
    calm = Color(0xFF4FD8C4),
    busy = Color(0xFFFDB022),
    heavy = Color(0xFFFF8A80),
    success = Color(0xFF47CD89),
    onSuccess = Color(0xFF053321),
    warningContainer = Color(0xFF4E2A06),
    onWarningContainer = Color(0xFFFEF0C7),
    track = Color(0xFF26303B),
)

/** 4/8dp spacing rhythm. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Horizontal page gutter. */
    val gutter = 20.dp
}

/** Shared motion tokens (Material 3 emphasized easing). Compose follows the system animation scale. */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 500
}

private val PaceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tight, confident type scale. Numbers use tabular figures so timers don't jitter. */
private val PaceTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
        displayMedium = displayMedium.copy(fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum"),
        displaySmall = displaySmall.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
        headlineLarge = headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineMedium = headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineSmall = headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
        labelSmall = labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    )
}

/** Monospaced-width numerals for counters and times inside normal text styles. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

private val LocalPaceColors = staticCompositionLocalOf { LightPace }

object PaceTheme {
    val colors: PaceColors
        @Composable get() = LocalPaceColors.current
}

@Composable
fun PaceMateTheme(content: @Composable () -> Unit) {
    // A fixed brand palette (not wallpaper colours) keeps the product recognisable
    // and keeps the status colours readable against known surfaces.
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalPaceColors provides if (dark) DarkPace else LightPace) {
        MaterialTheme(colorScheme = scheme, typography = PaceTypography, shapes = PaceShapes, content = content)
    }
}
