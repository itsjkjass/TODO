package com.pacemate.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AnimatedText
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.GradientButton
import com.pacemate.app.ui.GradientHero
import com.pacemate.app.ui.PaceIcons
import com.pacemate.app.ui.pulse
import com.pacemate.app.ui.staggerIn
import com.pacemate.app.ui.EmptyState
import com.pacemate.app.ui.Eyebrow
import com.pacemate.app.ui.PaceCard
import com.pacemate.app.ui.ProgressRing
import com.pacemate.app.ui.ScreenHeader
import com.pacemate.app.ui.SectionLabel
import com.pacemate.app.ui.StatTile
import com.pacemate.app.ui.StatusPill
import com.pacemate.app.ui.loadColor
import com.pacemate.app.ui.loadLabel
import com.pacemate.app.ui.longDate
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import com.pacemate.app.ui.theme.TabularNumbers
import com.pacemate.core.ActiveTimer
import com.pacemate.core.AppState
import com.pacemate.core.PlannedSession
import com.pacemate.core.SessionStatus
import com.pacemate.core.StateOps
import com.pacemate.core.Times
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

@Composable
fun TodayScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val today = LocalDate.now()
    val sessions = StateOps.sessionsOn(state, today)
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    val counted = sessions.filter { it.status != SessionStatus.SKIPPED }
    val planned = counted.sumOf { it.durationMinutes }
    val done = counted.filter { it.status == SessionStatus.DONE }.sumOf { it.durationMinutes }
    val left = counted.count { it.status == SessionStatus.PLANNED }
    val prefs = state.profile.preferences
    val isWorkDay = today.dayOfWeek.value in state.profile.routine.workDays
    val limit = ((if (isWorkDay) prefs.maxTaskHoursWorkday else prefs.maxTaskHoursFreeDay) * 60).toInt().coerceAtLeast(1)
    val activeTimer = state.activeTimer
    val activeSession = activeTimer?.let { t -> state.sessions.firstOrNull { it.id == t.sessionId } }
    val upNext = if (activeTimer == null) {
        sessions.firstOrNull { it.status == SessionStatus.PLANNED && it.endMinute > nowMinute }
            ?: sessions.firstOrNull { it.status == SessionStatus.PLANNED }
    } else null

    LazyColumn(
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            val streak = StateOps.streak(state, today)
            Column {
            ScreenHeader(
                title = greeting(state.profile.name),
                eyebrow = longDate(today),
                modifier = Modifier.staggerIn(0),
            ) {
                IconButton(onClick = { vm.openChat() }) {
                    Icon(PaceIcons.Chat, contentDescription = "Chat with PaceMate about your routine")
                }
                IconButton(onClick = { vm.replan(showMessage = true) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Re-plan my schedule")
                }
            }
            if (streak > 0) {
                StatusPill(
                    "$streak-day streak",
                    icon = PaceIcons.Flame,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.staggerIn(1),
                )
            }
            }
        }

        item {
            AnimatedVisibility(
                visible = activeSession != null && activeTimer != null,
                enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)),
                exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)),
            ) {
                if (activeSession != null && activeTimer != null) TimerHero(vm, activeSession, activeTimer)
            }
        }

        if (sessions.isNotEmpty()) {
            item { DaySummary(planned, done, left, limit, Modifier.staggerIn(2)) }
        }

        if (upNext != null) {
            item { UpNextCard(upNext, nowMinute, Modifier.staggerIn(3)) { vm.startTimer(upNext.id) } }
        }

        if (state.planWarnings.isNotEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = PaceTheme.colors.warningContainer,
                    contentColor = PaceTheme.colors.onWarningContainer,
                ) {
                    Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("Heads-up", style = MaterialTheme.typography.titleSmall)
                        }
                        state.planWarnings.forEach { Text(it.message, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.DateRange,
                    title = "Nothing planned today",
                    body = "Enjoy the free time. Add a goal in the Goals tab and it will be spread across the coming days.",
                )
            }
        } else {
            item { SectionLabel("Schedule") { Text("${sessions.size} blocks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            itemsIndexed(sessions, key = { _, it -> it.id }) { i, s ->
                TimelineRow(
                    vm = vm,
                    state = state,
                    s = s,
                    isLast = s.id == sessions.last().id,
                    isNow = s.startMinute <= nowMinute && nowMinute < s.endMinute,
                    modifier = Modifier.animateItem().staggerIn(4 + i),
                )
            }
        }

        val tomorrow = StateOps.sessionsOn(state, today.plusDays(1))
        if (tomorrow.isNotEmpty()) {
            item {
                PaceCard(container = MaterialTheme.colorScheme.surfaceContainer, border = false) {
                    Eyebrow("Tomorrow")
                    Text(
                        "${tomorrow.size} task${if (tomorrow.size == 1) "" else "s"} · ${Times.duration(tomorrow.sumOf { it.durationMinutes })}" +
                            " · first at ${Times.format(tomorrow.first().startMinute)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val part = when {
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, ${name.trim().substringBefore(' ')}"
}

@Composable
private fun DaySummary(planned: Int, done: Int, left: Int, limit: Int, modifier: Modifier = Modifier) {
    val ratio = planned.toFloat() / limit
    val on = PaceTheme.colors.onGradient
    GradientHero(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Space.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            ProgressRing(
                progress = if (planned == 0) 0f else done.toFloat() / planned,
                size = 108.dp,
                stroke = 11.dp,
                color = on,
                track = on.copy(alpha = 0.16f),
                description = "${Times.duration(done)} of ${Times.duration(planned)} done today",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedText(Times.duration(done), MaterialTheme.typography.titleLarge.merge(TabularNumbers), color = on)
                    Text("of ${Times.duration(planned)}", style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.75f))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Eyebrow(loadLabel(ratio), color = on.copy(alpha = 0.8f))
                AnimatedText(
                    if (left == 0) "All done!" else "$left to go",
                    MaterialTheme.typography.headlineMedium,
                    color = on,
                )
                Text(
                    "Comfortable limit ${Times.duration(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = on.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun UpNextCard(s: PlannedSession, nowMinute: Int, modifier: Modifier = Modifier, onStart: () -> Unit) {
    val startsIn = s.startMinute - nowMinute
    PaceCard(modifier = modifier, container = MaterialTheme.colorScheme.primaryContainer, border = false) {
        Eyebrow(
            when {
                startsIn > 0 -> "Up next · in ${Times.duration(startsIn)}"
                else -> "Up next · ready now"
            },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(s.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${s.goalTitle} · ${Times.format(s.startMinute)}–${Times.format(s.endMinute)} · ${Times.duration(s.durationMinutes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
        GradientButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(Space.sm))
            Text("Start focus")
        }
    }
}

/** Big live timer: ring shows how much of the planned time is used. */
@Composable
fun TimerHero(vm: AppViewModel, s: PlannedSession, timer: ActiveTimer) {
    val now = rememberNow(timer)
    val remainingMs = timer.plannedEndEpochMs - now
    val over = remainingMs < 0
    val elapsedSec = s.loggedSeconds + (now - timer.startedAtEpochMs) / 1000
    val totalSec = (s.durationMinutes * 60L).coerceAtLeast(1)
    val accent = if (over) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    PaceCard(
        modifier = Modifier.animateContentSize(),
        container = if (over) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = Space.xl,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            StatusPill(
                if (over) "Time's up" else "Focusing",
                container = accent.copy(alpha = 0.14f),
                content = accent,
            )
            ProgressRing(
                progress = if (over) 1f else elapsedSec.toFloat() / totalSec,
                size = 232.dp,
                stroke = 16.dp,
                color = accent,
                brush = if (over) null else PaceTheme.colors.gradient,
                glow = pulse(),
                description = if (over) "Over planned time by ${clock(abs(remainingMs) / 1000)}" else "${clock(remainingMs / 1000)} left",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        (if (over) "+" else "") + clock(abs(remainingMs) / 1000),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (over) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(if (over) "over plan" else "left", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(s.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${s.goalTitle} · planned ${Times.duration(s.durationMinutes)} · logged ${clock(elapsedSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(onClick = { vm.stopTimer(markDone = false) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Pause") }
                FilledTonalButton(onClick = { vm.extendTimer() }, modifier = Modifier.weight(1f).height(52.dp)) { Text("+10 min") }
                Button(
                    onClick = { vm.stopTimer(markDone = true) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("Done")
                }
            }
        }
    }
}

/** Compact running-timer bar shown above the navigation bar on the other tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniTimerBar(vm: AppViewModel, s: PlannedSession, timer: ActiveTimer, onOpen: () -> Unit) {
    val now = rememberNow(timer)
    val remainingMs = timer.plannedEndEpochMs - now
    val over = remainingMs < 0
    val accent = if (over) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = Space.lg, end = Space.xs).heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(accent, MaterialTheme.shapes.extraSmall))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (over) "Over by ${clock(abs(remainingMs) / 1000)}" else "${clock(remainingMs / 1000)} left",
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                )
            }
            IconButton(onClick = { vm.stopTimer(markDone = true) }) {
                Icon(Icons.Rounded.Check, contentDescription = "Mark ${s.title} done")
            }
        }
    }
}

@Composable
private fun rememberNow(key: Any): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

@Composable
private fun TimelineRow(vm: AppViewModel, state: AppState, s: PlannedSession, isLast: Boolean, isNow: Boolean, modifier: Modifier = Modifier) {
    val active = state.activeTimer?.sessionId == s.id
    val done = s.status == SessionStatus.DONE
    val skipped = s.status == SessionStatus.SKIPPED
    val rail = MaterialTheme.colorScheme.outlineVariant
    val dotColor = when {
        active || isNow -> MaterialTheme.colorScheme.primary
        done -> PaceTheme.colors.success
        else -> MaterialTheme.colorScheme.outline
    }

    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Time column
        Column(Modifier.width(52.dp).padding(top = Space.lg)) {
            Text(Times.format(s.startMinute), style = MaterialTheme.typography.labelLarge.merge(TabularNumbers))
            Text(Times.duration(s.durationMinutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Rail
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(Space.lg + 4.dp))
            if (done) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = dotColor, modifier = Modifier.size(14.dp))
            } else {
                Box(Modifier.size(12.dp).background(dotColor, MaterialTheme.shapes.extraLarge))
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).padding(top = Space.xs).background(rail))
        }
        // Card
        PaceCard(
            modifier = Modifier.weight(1f).padding(bottom = Space.sm).alpha(if (done || skipped) 0.6f else 1f),
            container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            border = !active,
            contentPadding = Space.md,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(s.goalTitle)
                            if (s.loggedSeconds > 0) append(" · logged ${clock(s.loggedSeconds)}")
                            if (skipped) append(" · skipped, moved later")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (s.difficulty >= 3 && !done) {
                        StatusPill(
                            "Deep work",
                            modifier = Modifier.padding(top = Space.xs),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                if (s.status == SessionStatus.PLANNED && !active) {
                    FilledIconButton(onClick = { vm.startTimer(s.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Start timer for ${s.title}")
                    }
                    SessionMenu(vm, s)
                } else if (active) {
                    StatusPill("Running", container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun SessionMenu(vm: AppViewModel, s: PlannedSession) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "More actions for ${s.title}" }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Mark this block done") }, onClick = { open = false; vm.markDone(s.id) })
            DropdownMenuItem(text = { Text("Whole step finished") }, onClick = { open = false; vm.completeSubtask(s.goalId, s.subtaskId) })
            DropdownMenuItem(text = { Text("Skip (plan it later)") }, onClick = { open = false; vm.skipSession(s.id) })
        }
    }
}

fun clock(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
