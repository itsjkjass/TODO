package com.pacemate.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- custom icons

/** A few brand icons not in the core Material set, drawn on the standard 24x24 grid. */
object PaceIcons {
    val Flame: ImageVector by lazy {
        ImageVector.Builder("Flame", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(12.5f, 5.5f, 9f, 7.5f, 7.5f, 10.5f)
                curveTo(6.5f, 12.5f, 6f, 14f, 6f, 15.5f)
                curveTo(6f, 19.1f, 8.7f, 22f, 12f, 22f)
                curveTo(15.3f, 22f, 18f, 19.1f, 18f, 15.5f)
                curveTo(18f, 12.5f, 16.5f, 10f, 15f, 8.5f)
                curveTo(15f, 10.5f, 14f, 12f, 12.8f, 12.5f)
                curveTo(13.5f, 9f, 13.5f, 5f, 12f, 2f)
                close()
            }
        }.build()
    }

    val Sparkle: ImageVector by lazy {
        ImageVector.Builder("Sparkle", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(12.6f, 7.2f, 14.8f, 9.4f, 22f, 12f)
                curveTo(14.8f, 14.6f, 12.6f, 16.8f, 12f, 22f)
                curveTo(11.4f, 16.8f, 9.2f, 14.6f, 2f, 12f)
                curveTo(9.2f, 9.4f, 11.4f, 7.2f, 12f, 2f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 2f)
                curveTo(19.3f, 3.6f, 19.9f, 4.4f, 21.5f, 4.8f)
                curveTo(19.9f, 5.2f, 19.3f, 6f, 19f, 7.6f)
                curveTo(18.7f, 6f, 18.1f, 5.2f, 16.5f, 4.8f)
                curveTo(18.1f, 4.4f, 18.7f, 3.6f, 19f, 2f)
                close()
            }
        }.build()
    }

    val Chat: ImageVector by lazy {
        ImageVector.Builder("Chat", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 3f)
                horizontalLineTo(19f)
                curveTo(20.7f, 3f, 22f, 4.3f, 22f, 6f)
                verticalLineTo(15f)
                curveTo(22f, 16.7f, 20.7f, 18f, 19f, 18f)
                horizontalLineTo(9f)
                lineTo(4.5f, 21.6f)
                curveTo(3.8f, 22.1f, 3f, 21.6f, 3f, 20.8f)
                lineTo(2f, 18f)
                verticalLineTo(6f)
                curveTo(2f, 4.3f, 3.3f, 3f, 5f, 3f)
                close()
            }
        }.build()
    }
}

// ---------------------------------------------------------------- motion modifiers

/** Fades and lifts content in on first appearance; [index] staggers items in a list. */
fun Modifier.staggerIn(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * Motion.STAGGER).toLong())
        progress.animateTo(1f, tween(Motion.LONG, easing = Motion.EmphasizedDecelerate))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Gentle shrink while pressed, with a springy release. Layout bounds never change. */
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

// ---------------------------------------------------------------- gradient surfaces

/** The brand gradient, slowly drifting so hero surfaces feel alive. */
@Composable
fun animatedBrandBrush(): Brush {
    val colors = PaceTheme.colors.gradient
    val t = rememberInfiniteTransition(label = "brand")
    val shift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(-400f + 800f * shift, 0f),
        end = Offset(400f + 800f * shift, 900f),
    )
}

/** A large rounded surface painted with the drifting brand gradient. Content uses onGradient colours. */
@Composable
fun GradientHero(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .clip(shape)
            .background(animatedBrandBrush())
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides PaceTheme.colors.onGradient) {
            content()
        }
    }
}

/** Primary call-to-action painted with the brand gradient. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val brush = if (enabled) animatedBrandBrush() else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
    Row(
        modifier
            .heightIn(min = 56.dp)
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.medium)
            .background(brush)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) PaceTheme.colors.onGradient else MaterialTheme.colorScheme.onSurfaceVariant,
            LocalTextStyle provides MaterialTheme.typography.titleMedium,
        ) { content() }
    }
}

// ---------------------------------------------------------------- text & indicators

/** Text that rolls vertically when its value changes (counters, timers, totals). */
@Composable
fun AnimatedText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { it / 2 } + fadeIn(tween(Motion.MEDIUM)))
                .togetherWith(slideOutVertically(tween(Motion.SHORT)) { -it / 2 } + fadeOut(tween(Motion.SHORT)))
        },
        modifier = modifier,
        label = "animatedText",
    ) { value -> Text(value, style = style, color = color) }
}

/** Three bouncing dots: "PaceMate is typing". */
@Composable
fun TypingDots(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val y by t.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 150, easing = Motion.Emphasized), RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer { translationY = -y * 5.dp.toPx(); alpha = 0.45f + 0.55f * y }
                    .background(color, CircleShape),
            )
        }
    }
}

/** A soft pulsing halo, used behind the running timer. */
@Composable
fun pulse(periodMs: Int = 1600): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = Motion.Emphasized), RepeatMode.Reverse), label = "pulseValue")
    return v
}

// ---------------------------------------------------------------- celebration

private data class Particle(val angle: Float, val speed: Float, val size: Float, val colorIndex: Int, val spin: Float)

/**
 * A short confetti burst. Re-runs whenever [trigger] changes (ignoring 0).
 * Purely decorative: it draws over the screen but never takes touches.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val colors = PaceTheme.colors.gradient + PaceTheme.colors.success
    val particles = remember(trigger) {
        val rnd = Random(trigger)
        List(70) {
            Particle(
                angle = (rnd.nextFloat() * 180f + 180f) * (Math.PI.toFloat() / 180f),
                speed = 0.55f + rnd.nextFloat() * 0.75f,
                size = 6f + rnd.nextFloat() * 8f,
                colorIndex = rnd.nextInt(colors.size),
                spin = rnd.nextFloat() * 6f,
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1400, easing = LinearEasing)) }
    if (progress.value >= 1f) return

    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val origin = Offset(size.width / 2f, size.height * 0.62f)
        val reach = size.height * 0.75f
        particles.forEach { part ->
            val dist = reach * part.speed * p
            val gravity = size.height * 0.9f * p * p
            val x = origin.x + cos(part.angle) * dist
            val y = origin.y + sin(part.angle) * dist + gravity
            val alpha = (1f - p).coerceIn(0f, 1f)
            val w = part.size * (0.6f + 0.4f * cos(part.spin * p * 6f))
            drawRect(
                color = colors[part.colorIndex].copy(alpha = alpha),
                topLeft = Offset(x - w / 2, y - part.size / 2),
                size = androidx.compose.ui.geometry.Size(w.coerceAtLeast(1f), part.size),
            )
        }
    }
}

/** Circle avatar painted with the brand gradient (the assistant's face in chat). */
@Composable
fun BrandAvatar(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(animatedBrandBrush()),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(PaceIcons.Sparkle, contentDescription = null, tint = PaceTheme.colors.onGradient, modifier = Modifier.size(size * 0.5f))
    }
}
