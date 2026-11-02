package com.pacemate.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.DAY_NAMES
import com.pacemate.app.ui.Dot
import com.pacemate.app.ui.EmptyState
import com.pacemate.app.ui.Eyebrow
import com.pacemate.app.ui.LoadBar
import com.pacemate.app.ui.PaceCard
import com.pacemate.app.ui.ScreenHeader
import com.pacemate.app.ui.SectionLabel
import com.pacemate.app.ui.StatusPill
import com.pacemate.app.ui.loadColor
import com.pacemate.app.ui.loadLabel
import com.pacemate.app.ui.prettyDate
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import com.pacemate.app.ui.theme.TabularNumbers
import com.pacemate.core.AppState
import com.pacemate.core.SessionStatus
import com.pacemate.core.Times
import java.time.LocalDate

private fun dayLimit(state: AppState, d: LocalDate): Int {
    val p = state.profile.preferences
    val isWork = d.dayOfWeek.value in state.profile.routine.workDays
    return ((if (isWork) p.maxTaskHoursWorkday else p.maxTaskHoursFreeDay) * 60).toInt().coerceAtLeast(1)
}

/** The upcoming days: a week-at-a-glance chart, then each day's blocks. */
@Composable
fun PlanScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val today = LocalDate.now()
    val days = state.sessions
        .filter { it.date >= today.toString() && it.status != SessionStatus.SKIPPED }
        .groupBy { it.date }
        .toSortedMap()
    val totalPlanned = days.values.sumOf { list -> list.filter { it.status == SessionStatus.PLANNED }.sumOf { it.durationMinutes } }

    LazyColumn(
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            ScreenHeader(
                title = "Plan",
                eyebrow = "Next ${days.size} day${if (days.size == 1) "" else "s"}",
                subtitle = if (days.isEmpty()) null else "${Times.duration(totalPlanned)} of focused work ahead, spread so no day is overloaded.",
            )
        }

        if (days.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.DateRange,
                    title = "Your calendar is clear",
                    body = "Add a goal in the Goals tab. It's broken into steps and spread across the days before its deadline.",
                )
            }
            return@LazyColumn
        }

        item { WeekChart(state, today) }

        items(days.entries.toList(), key = { it.key }) { (date, list) ->
            val d = LocalDate.parse(date)
            val limit = dayLimit(state, d)
            val total = list.sumOf { it.durationMinutes }
            val ratio = total.toFloat() / limit
            PaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(prettyDate(d), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${Times.duration(total)} planned · limit ${Times.duration(limit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(loadLabel(ratio), container = loadColor(ratio).copy(alpha = 0.14f), content = loadColor(ratio))
                }
                LoadBar(ratio)
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    list.sortedBy { it.startMinute }.forEach { s ->
                        val done = s.status == SessionStatus.DONE
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                Times.format(s.startMinute),
                                Modifier.width(52.dp),
                                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (done) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = PaceTheme.colors.success, modifier = Modifier.padding(end = Space.sm).width(16.dp))
                            } else {
                                Dot(
                                    if (s.difficulty >= 3) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    Modifier.padding(end = Space.sm),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.goalTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(Times.duration(s.durationMinutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Seven bars, one per day, against a dashed line for the comfortable limit. */
@Composable
private fun WeekChart(state: AppState, today: LocalDate) {
    val week = (0L until 7L).map { today.plusDays(it) }
    val minutes = week.map { d ->
        state.sessions.filter { it.date == d.toString() && it.status != SessionStatus.SKIPPED }.sumOf { it.durationMinutes }
    }
    val limits = week.map { dayLimit(state, it) }
    val maxValue = (minutes + limits).maxOrNull()?.coerceAtLeast(60) ?: 60
    val track = PaceTheme.colors.track
    val limitColor = MaterialTheme.colorScheme.outline
    val colors = week.indices.map { i -> loadColor(minutes[i].toFloat() / limits[i]) }
    val growAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { growAnim.animateTo(1f, tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) }
    val grow = growAnim.value
    val description = week.indices.joinToString(". ") { i ->
        "${DAY_NAMES[week[i].dayOfWeek.value - 1]} ${Times.duration(minutes[i])} of ${Times.duration(limits[i])}"
    }

    PaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("This week", Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Dot(PaceTheme.colors.calm)
                    Text("Planned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Box(Modifier.width(12.dp).height(2.dp).background(limitColor))
                    Text("Your limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Planned time per day. $description" },
        ) {
            val n = 7
            val gap = 12.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            for (i in 0 until n) {
                val x = i * (barW + gap)
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), radius)
                val h = size.height * (minutes[i].toFloat() / maxValue).coerceIn(0f, 1f) * grow
                if (h > 0f) drawRoundRect(colors[i], Offset(x, size.height - h), Size(barW, h), radius)
                val ly = size.height - size.height * (limits[i].toFloat() / maxValue).coerceIn(0f, 1f)
                drawLine(
                    limitColor, Offset(x - 2.dp.toPx(), ly), Offset(x + barW + 2.dp.toPx(), ly), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (i == 0) "Today" else DAY_NAMES[d.dayOfWeek.value - 1],
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, maxLines = 1,
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
