package com.pacemate.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.DAY_NAMES
import com.pacemate.app.ui.EmptyState
import com.pacemate.app.ui.Eyebrow
import com.pacemate.app.ui.PaceCard
import com.pacemate.app.ui.ScreenHeader
import com.pacemate.app.ui.SectionLabel
import com.pacemate.app.ui.StatTile
import com.pacemate.app.ui.StatusPill
import com.pacemate.app.ui.prettyDate
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import com.pacemate.app.ui.theme.TabularNumbers
import com.pacemate.core.PaceModel
import com.pacemate.core.Times
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The logger history: what time was really spent, and the pace the app has learned. */
@Composable
fun LogScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val byDay = state.logs.sortedByDescending { it.startEpochMs }.groupBy { dayOf(it.startEpochMs) }
    val todaySec = byDay[today]?.sumOf { it.seconds } ?: 0L
    val weekStart = today.minusDays(6)
    val weekSec = state.logs.filter { !dayOf(it.startEpochMs).isBefore(weekStart) }.sumOf { it.seconds }
    val pacePct = (state.pace.factor * 100).roundToInt()

    LazyColumn(
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { ScreenHeader(title = "Time log", eyebrow = "Logger", subtitle = "Every minute you time trains your personal pace.") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                StatTile("Today", Times.duration((todaySec / 60).toInt()), Modifier.weight(1f))
                StatTile("Last 7 days", Times.duration((weekSec / 60).toInt()), Modifier.weight(1f))
                StatTile("Your pace", "$pacePct%", Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
            }
        }

        if (state.logs.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.PlayArrow,
                    title = "No time logged yet",
                    body = "Press play on any block in Today. When you finish, I learn how long things really take you.",
                )
            }
            return@LazyColumn
        }

        item { FocusChart(today) { d -> byDay[d]?.sumOf { it.seconds } ?: 0L } }

        item {
            PaceCard {
                Eyebrow("What I've learned")
                Text(PaceModel.describe(state.pace), style = MaterialTheme.typography.bodyLarge)
                if (state.pace.categoryFactors.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    state.pace.categoryFactors.entries.sortedBy { it.key }.forEach { (cat, f) ->
                        val n = state.pace.categorySamples[cat] ?: 0
                        val pct = (f * 100).roundToInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("$n session${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$pct%", style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                                modifier = Modifier.width(56.dp), textAlign = TextAlign.End,
                                color = if (pct > 105) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text("100% = a typical estimate. Higher means you take longer, and plans give you that time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        byDay.forEach { (day, logs) ->
            item(key = "h-$day") {
                SectionLabel(prettyDate(day)) {
                    Text(Times.duration((logs.sumOf { it.seconds } / 60).toInt()), style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "d-$day") {
                PaceCard(contentPadding = Space.md) {
                    logs.forEachIndexed { i, l ->
                        val start = Instant.ofEpochMilli(l.startEpochMs).atZone(zone).toLocalTime()
                        val actualMin = (l.seconds / 60.0).roundToInt()
                        val over = actualMin > l.plannedMinutes
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d:%02d".format(start.hour, start.minute),
                                Modifier.width(52.dp),
                                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(l.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("planned ${Times.duration(l.plannedMinutes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                clock(l.seconds) + if (over) " · over" else "",
                                container = if (over) MaterialTheme.colorScheme.secondaryContainer else PaceTheme.colors.calm.copy(alpha = 0.14f),
                                content = if (over) MaterialTheme.colorScheme.onSecondaryContainer else PaceTheme.colors.calm,
                            )
                        }
                        if (i < logs.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/** Logged focus time for the last 7 days. */
@Composable
private fun FocusChart(today: LocalDate, secondsOn: (LocalDate) -> Long) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val minutes = days.map { (secondsOn(it) / 60).toInt() }
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(30)
    val bar = MaterialTheme.colorScheme.primary
    val track = PaceTheme.colors.track
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) }
    val description = days.indices.joinToString(". ") { "${DAY_NAMES[days[it].dayOfWeek.value - 1]} ${Times.duration(minutes[it])}" }

    PaceCard {
        Eyebrow("Focus time · last 7 days")
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Logged focus time per day. $description" }) {
            val n = days.size
            val gap = 12.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            val r = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            for (i in 0 until n) {
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
                val h = size.height * (minutes[i].toFloat() / max).coerceIn(0f, 1f) * grow.value
                if (h > 0f) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), r)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (d == today) "Today" else DAY_NAMES[d.dayOfWeek.value - 1],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        if (minutes[i] == 0) "–" else Times.duration(minutes[i]),
                        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
    }
}
