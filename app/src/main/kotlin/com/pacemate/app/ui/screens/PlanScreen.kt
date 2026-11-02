package com.pacemate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.SectionCard
import com.pacemate.app.ui.prettyDate
import com.pacemate.app.ui.theme.LoadColors
import com.pacemate.core.SessionStatus
import com.pacemate.core.Times
import java.time.LocalDate

/** The upcoming days with their tasks and how full each day is. */
@Composable
fun PlanScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val today = LocalDate.now().toString()
    val days = state.sessions
        .filter { it.date >= today && it.status != SessionStatus.SKIPPED }
        .groupBy { it.date }
        .toSortedMap()
    val prefs = state.profile.preferences

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Upcoming plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bars show each day's load against your comfortable limit.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (days.isEmpty()) {
            item { SectionCard("Nothing planned", subtitle = "Add a goal in the Goals tab and it will show up here.") {} }
        }
        items(days.entries.toList(), key = { it.key }) { (date, list) ->
            val d = LocalDate.parse(date)
            val isWork = d.dayOfWeek.value in state.profile.routine.workDays
            val limit = ((if (isWork) prefs.maxTaskHoursWorkday else prefs.maxTaskHoursFreeDay) * 60).toInt().coerceAtLeast(1)
            val total = list.sumOf { it.durationMinutes }
            val ratio = total.toFloat() / limit
            SectionCard(prettyDate(d), subtitle = "${Times.duration(total)} planned · limit ${Times.duration(limit)}") {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                            .background(
                                when {
                                    ratio <= 0.75f -> LoadColors.Calm
                                    ratio <= 1.0f -> LoadColors.Busy
                                    else -> LoadColors.Heavy
                                },
                            ),
                    )
                }
                list.sortedBy { it.startMinute }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Times.format(s.startMinute)}–${Times.format(s.endMinute)}", Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.weight(1f)) {
                            Text(s.title + if (s.status == SessionStatus.DONE) "  ✓" else "", style = MaterialTheme.typography.bodyMedium)
                            Text(s.goalTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
