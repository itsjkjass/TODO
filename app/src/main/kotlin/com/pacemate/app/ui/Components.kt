package com.pacemate.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import com.pacemate.app.ui.theme.TabularNumbers
import com.pacemate.core.Times
import java.time.LocalDate
import kotlin.math.roundToInt

val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

// ---------------------------------------------------------------- surfaces

/** The base card: flat, bright surface with a hairline border (no heavy shadows). */
@Composable
fun PaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: Boolean = true,
    contentPadding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val stroke = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(Space.md), content = content)
    }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Card(
            onClick = onClick, modifier = modifier.fillMaxWidth().pressScale(interaction), shape = MaterialTheme.shapes.large,
            colors = colors, border = stroke, interactionSource = interaction, content = inner,
        )
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = stroke, content = inner)
    }
}

/** A titled card used by forms and settings. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    PaceCard(modifier.padding(vertical = Space.xs)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/** Large page title with an optional eyebrow line and trailing actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.sm), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (eyebrow != null) Eyebrow(eyebrow, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Small all-caps label above content. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

/** Section title inside a scrolling page. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

// ---------------------------------------------------------------- data display

/** Circular progress with content in the middle. Animated, and readable by screen readers. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = PaceTheme.colors.track,
    description: String? = null,
    /** Paint the progress arc with this instead of [color] (e.g. the brand gradient). */
    brush: List<Color>? = null,
    /** 0..1 strength of a soft halo behind the arc (animate it for a pulse). */
    glow: Float = 0f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val glowColor = PaceTheme.colors.glow
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(Motion.LONG, easing = Motion.Emphasized),
        label = "ring",
    )
    Box(
        modifier.size(size).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val px = stroke.toPx()
            val inset = px / 2
            val arcSize = Size(this.size.width - px, this.size.height - px)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(px))
            if (animated > 0f) {
                if (glow > 0f) {
                    drawArc(
                        glowColor.copy(alpha = glowColor.alpha * glow), startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(px * 2.4f, cap = StrokeCap.Round),
                    )
                }
                if (brush != null) {
                    rotate(-90f) {
                        drawArc(
                            Brush.sweepGradient(brush + brush.first()), startAngle = 0f, sweepAngle = 360f * animated, useCenter = false,
                            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(px, cap = StrokeCap.Round),
                        )
                    }
                } else {
                    drawArc(
                        color, startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(px, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/** Horizontal load bar: how full a day is against the user's comfortable limit. */
@Composable
fun LoadBar(ratio: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val animated by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(Motion.LONG, easing = Motion.Emphasized), label = "load")
    Box(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(PaceTheme.colors.track),
    ) {
        if (animated > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceAtLeast(0.02f)).clip(CircleShape).background(loadColor(ratio)))
        }
    }
}

@Composable
fun loadColor(ratio: Float): Color = when {
    ratio <= 0.75f -> PaceTheme.colors.calm
    ratio <= 1.0f -> PaceTheme.colors.busy
    else -> PaceTheme.colors.heavy
}

fun loadLabel(ratio: Float): String = when {
    ratio <= 0f -> "Free day"
    ratio <= 0.5f -> "Light day"
    ratio <= 0.75f -> "Comfortable"
    ratio <= 1.0f -> "Full day"
    else -> "Over your limit"
}

/** A compact metric: big number, small label. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(value, style = MaterialTheme.typography.titleLarge.merge(TabularNumbers), color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A small rounded status label. Always carries text, so colour is never the only signal. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Icon inside a softly tinted circle. Decorative: hidden from screen readers. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Friendly empty state with one clear next action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xxl, horizontal = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        IconBadge(icon, size = 64.dp)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.xs))
            action()
        }
    }
}

// ---------------------------------------------------------------- form controls

/** A row with a label and a pill showing the time; opens the system time picker. */
@Composable
fun TimeField(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(
            onClick = {
                val m = ((minute % 1440) + 1440) % 1440
                TimePickerDialog(context, { _, h, mm -> onChange(h * 60 + mm) }, m / 60, m % 60, true).show()
            },
            modifier = Modifier.semantics { contentDescription = "$label, ${Times.format(minute)}. Change time" },
        ) { Text(Times.format(minute), style = MaterialTheme.typography.titleMedium.merge(TabularNumbers)) }
    }
}

@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(
            onClick = {
                DatePickerDialog(context, { _, y, mo, d -> onChange(LocalDate.of(y, mo + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth)
                    .apply { datePicker.minDate = System.currentTimeMillis() - 1000 }
                    .show()
            },
            modifier = Modifier.semantics { contentDescription = "$label, ${prettyDate(date)}. Change date" },
        ) { Text(prettyDate(date), style = MaterialTheme.typography.titleMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        DAY_NAMES.forEachIndexed { i, name ->
            val day = i + 1
            FilterChip(
                selected = day in selected,
                onClick = { onChange(if (day in selected) selected - day else selected + day) },
                label = { Text(name) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(display(value), style = MaterialTheme.typography.titleSmall.merge(TabularNumbers), color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            modifier = Modifier.semantics { contentDescription = "$label: ${display(value)}" },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
fun HSpace(w: Int = 8) = Spacer(Modifier.width(w.dp))

// ---------------------------------------------------------------- formatting

fun prettyDate(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> "${DAY_NAMES[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${monthShort(d)}"
    }
}

fun monthShort(d: LocalDate) = d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

fun longDate(d: LocalDate): String =
    "${d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${d.dayOfMonth} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }}"

fun hoursLabel(h: Float): String = Times.duration((h * 60).roundToInt())

/** "in 3 days", "tomorrow", "today", "2 days ago". */
fun relativeDays(d: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        days == 0L -> "due today"
        days == 1L -> "due tomorrow"
        days > 1 -> "$days days left"
        days == -1L -> "1 day overdue"
        else -> "${-days} days overdue"
    }
}

/** Rounded tiny dot used as a bullet / legend marker. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(50)).background(color))
}
