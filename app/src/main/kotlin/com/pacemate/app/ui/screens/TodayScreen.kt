package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.theme.LoadColors
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
    val planned = sessions.filter { it.status != SessionStatus.SKIPPED }.sumOf { it.durationMinutes }
    val done = sessions.filter { it.status == SessionStatus.DONE }.sumOf { it.durationMinutes }
    val isWorkDay = today.dayOfWeek.value in state.profile.routine.workDays
    val limit = ((if (isWorkDay) state.profile.preferences.maxTaskHoursWorkday else state.profile.preferences.maxTaskHoursFreeDay) * 60).toInt()

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(state.profile.name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (sessions.isEmpty()) "Nothing planned today. Enjoy it."
                        else "${Times.duration(done)} of ${Times.duration(planned)} done · comfortable limit ${Times.duration(limit)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = { vm.replan(showMessage = true) }) { Icon(Icons.Default.Refresh, "Re-plan") }
            }
            if (planned > 0) {
                val ratio = planned.toFloat() / limit.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { (done.toFloat() / planned).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = when {
                        ratio <= 0.75f -> LoadColors.Calm
                        ratio <= 1.0f -> LoadColors.Busy
                        else -> LoadColors.Heavy
                    },
                )
            }
        }

        state.activeTimer?.let { timer ->
            val s = state.sessions.firstOrNull { it.id == timer.sessionId }
            if (s != null) item { ActiveTimerCard(vm, s, timer) }
        }

        if (state.planWarnings.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null); Spacer(Modifier.width(8.dp))
                            Text("Heads-up", fontWeight = FontWeight.SemiBold)
                        }
                        state.planWarnings.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }

        items(sessions, key = { it.id }) { s ->
            SessionCard(vm, state, s, isNowOrPast = s.startMinute <= nowMinute)
        }

        val tomorrow = StateOps.sessionsOn(state, today.plusDays(1))
        if (tomorrow.isNotEmpty()) {
            item {
                Text(
                    "Tomorrow: ${tomorrow.size} task(s), ${Times.duration(tomorrow.sumOf { it.durationMinutes })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val part = when {
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, $name"
}

@Composable
fun ActiveTimerCard(vm: AppViewModel, s: PlannedSession, timer: ActiveTimer) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remainingMs = timer.plannedEndEpochMs - now
    val over = remainingMs < 0
    val elapsedSec = s.loggedSeconds + (now - timer.startedAtEpochMs) / 1000

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (over) "Time's up - over by" else "Time left", style = MaterialTheme.typography.labelLarge)
            Text(
                clock(abs(remainingMs) / 1000),
                fontSize = 48.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = if (over) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${s.goalTitle} · planned ${Times.duration(s.durationMinutes)} · logged ${clock(elapsedSec)}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.stopTimer(markDone = true) }) { Icon(Icons.Default.Check, null); Text("Done") }
                FilledTonalButton(onClick = { vm.extendTimer() }) { Text("+10 min") }
                OutlinedButton(onClick = { vm.stopTimer(markDone = false) }) { Text("Pause") }
            }
        }
    }
}

@Composable
private fun SessionCard(vm: AppViewModel, state: AppState, s: PlannedSession, isNowOrPast: Boolean) {
    val active = state.activeTimer?.sessionId == s.id
    val doneOrSkipped = s.status != SessionStatus.PLANNED
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                active -> MaterialTheme.colorScheme.primaryContainer
                doneOrSkipped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(64.dp)) {
                    Text(Times.format(s.startMinute), fontWeight = FontWeight.Bold)
                    Text(Times.duration(s.durationMinutes), style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (s.status == SessionStatus.DONE) TextDecoration.LineThrough else null,
                    )
                    Text(
                        buildString {
                            append(s.goalTitle)
                            if (s.difficulty >= 3) append(" · deep work")
                            if (s.loggedSeconds > 0) append(" · logged ${clock(s.loggedSeconds)}")
                            if (s.status == SessionStatus.SKIPPED) append(" · skipped (re-planned)")
                            if (s.status == SessionStatus.PLANNED && isNowOrPast && !active) append(" · ready")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (s.status == SessionStatus.PLANNED && !active) {
                    IconButton(onClick = { vm.startTimer(s.id) }) { Icon(Icons.Default.PlayArrow, "Start timer") }
                }
            }
            if (s.status == SessionStatus.PLANNED) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { vm.skipSession(s.id) }) { Text("Skip") }
                    TextButton(onClick = { vm.markDone(s.id) }) { Text("Done") }
                    TextButton(onClick = { vm.completeSubtask(s.goalId, s.subtaskId) }) { Text("Whole step finished") }
                }
            }
        }
    }
}

fun clock(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
