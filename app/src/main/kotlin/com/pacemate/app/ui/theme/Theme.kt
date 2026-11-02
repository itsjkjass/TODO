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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * PaceMate design system: "Sunset energy"
 * ---------------------------------------
 * Warm coral -> pink -> orange gradients on a near-black (dark) or warm-white (light)
 * canvas, with lime for success. The app follows the phone's light/dark setting.
 *
 * Contrast rules:
 *  - Body text is at least 4.5:1 against its surface in both themes.
 *  - Text on the brand gradient always uses [PaceColors.onGradient] (deep plum). White
 *    text fails on the orange end of the gradient, so it is never used there.
 *  - In light mode the solid primary is a deeper coral, so white button text passes 4.5:1.
 * Screens read colours from MaterialTheme.colorScheme / PaceTheme.colors, never raw hex.
 */

private val Coral = Color(0xFFFF6B6B)
private val Pink = Color(0xFFF472B6)
private val Orange = Color(0xFFFB923C)
private val Plum = Color(0xFF1A0B10)

private val DarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF2A0A0C),
    primaryContainer = Color(0xFF4A1D22),
    onPrimaryContainer = Color(0xFFFFD9D6),
    inversePrimary = Color(0xFFD23A4E),
    secondary = Orange,
    onSecondary = Color(0xFF2B1400),
    secondaryContainer = Color(0xFF4A2A10),
    onSecondaryContainer = Color(0xFFFFE1C7),
    tertiary = Pink,
    onTertiary = Color(0xFF2E0A1F),
    tertiaryContainer = Color(0xFF4D1D37),
    onTertiaryContainer = Color(0xFFFFD8EC),
    background = Color(0xFF0F0B0E),
    onBackground = Color(0xFFFFF5F5),
    surface = Color(0xFF0F0B0E),
    onSurface = Color(0xFFFFF5F5),
    surfaceVariant = Color(0xFF2B2026),
    onSurfaceVariant = Color(0xFFB8A9AE),
    surfaceTint = Coral,
    inverseSurface = Color(0xFFFFF5F5),
    inverseOnSurface = Color(0xFF1C1519),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF7A6870),
    outlineVariant = Color(0xFF3A2C33),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3A2C33),
    surfaceDim = Color(0xFF0F0B0E),
    surfaceContainerLowest = Color(0xFF0A0709),
    surfaceContainerLow = Color(0xFF1C1519),
    surfaceContainer = Color(0xFF231A1F),
    surfaceContainerHigh = Color(0xFF2B2026),
    surfaceContainerHighest = Color(0xFF33262D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD23A4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E1),
    onPrimaryContainer = Color(0xFF5A0E1A),
    inversePrimary = Coral,
    secondary = Color(0xFFC2410C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7D3),
    onSecondaryContainer = Color(0xFF4A1C04),
    tertiary = Color(0xFFC2187A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9EC),
    onTertiaryContainer = Color(0xFF4A0B2C),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF23161A),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF23161A),
    surfaceVariant = Color(0xFFF5DEDA),
    onSurfaceVariant = Color(0xFF6B5A60),
    surfaceTint = Color(0xFFD23A4E),
    inverseSurface = Color(0xFF2B2026),
    inverseOnSurface = Color(0xFFFFF5F5),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFA08C92),
    outlineVariant = Color(0xFFF0DCD8),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFF2E2DE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFF1EE),
    surfaceContainerHigh = Color(0xFFFBE8E4),
    surfaceContainerHighest = Color(0xFFF5DEDA),
)

/** Colours Material doesn't name: gradient, load levels, outcomes. */
@Immutable
data class PaceColors(
    /** Brand gradient stops: coral -> pink -> orange. */
    val gradient: List<Color>,
    /** Text/icons drawn on top of the brand gradient. */
    val onGradient: Color,
    val calm: Color,
    val busy: Color,
    val heavy: Color,
    val success: Color,
    val onSuccess: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val track: Color,
    val glow: Color,
    val userBubble: List<Color>,
    val botBubble: Color,
) {
    val brush: Brush get() = Brush.linearGradient(gradient)
}

private val DarkPace = PaceColors(
    gradient = listOf(Coral, Pink, Orange),
    onGradient = Plum,
    calm = Color(0xFFA3E635),
    busy = Orange,
    heavy = Color(0xFFFF5C7A),
    success = Color(0xFFA3E635),
    onSuccess = Color(0xFF1A2E05),
    warningContainer = Color(0xFF4A2A10),
    onWarningContainer = Color(0xFFFFE1C7),
    track = Color(0xFF2E2329),
    glow = Coral.copy(alpha = 0.35f),
    userBubble = listOf(Coral, Pink),
    botBubble = Color(0xFF231A1F),
)

private val LightPace = PaceColors(
    gradient = listOf(Coral, Pink, Orange),
    onGradient = Plum,
    calm = Color(0xFF4D7C0F),
    busy = Color(0xFFC2410C),
    heavy = Color(0xFFBA1A1A),
    success = Color(0xFF4D7C0F),
    onSuccess = Color.White,
    warningContainer = Color(0xFFFFE7D3),
    onWarningContainer = Color(0xFF4A1C04),
    track = Color(0xFFF5E4E0),
    glow = Coral.copy(alpha = 0.25f),
    userBubble = listOf(Coral, Pink),
    botBubble = Color(0xFFFFF1EE),
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
    const val STAGGER = 45
}

private val PaceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Bold, tight type scale. Numbers use tabular figures so counters don't jitter. */
private val PaceTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2.5).sp, fontFeatureSettings = "tnum"),
        displayMedium = displayMedium.copy(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
        displaySmall = displaySmall.copy(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp, fontFeatureSettings = "tnum"),
        headlineLarge = headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp),
        headlineSmall = headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
        labelSmall = labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )
}

/** Monospaced-width numerals for counters and times inside normal text styles. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

private val LocalPaceColors = staticCompositionLocalOf { DarkPace }

object PaceTheme {
    val colors: PaceColors
        @Composable get() = LocalPaceColors.current
}

@Composable
fun PaceMateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalPaceColors provides if (dark) DarkPace else LightPace) {
        MaterialTheme(colorScheme = scheme, typography = PaceTypography, shapes = PaceShapes, content = content)
    }
}
