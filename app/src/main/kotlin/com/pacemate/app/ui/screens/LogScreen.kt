package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.SectionCard
import com.pacemate.app.ui.prettyDate
import com.pacemate.core.PaceModel
import com.pacemate.core.Times
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** The logger history: what time was really spent on which task, and the learned pace. */
@Composable
fun LogScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val zone = ZoneId.systemDefault()
    val byDay = state.logs.sortedByDescending { it.startEpochMs }
        .groupBy { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Time log", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SectionCard("Your pace") {
                Text(PaceModel.describe(state.pace))
                if (state.pace.categoryFactors.isNotEmpty()) {
                    state.pace.categoryFactors.forEach { (cat, f) ->
                        val n = state.pace.categorySamples[cat] ?: 0
                        Text("• $cat: ${(f * 100).roundToInt()}% of a typical estimate ($n session${if (n == 1) "" else "s"})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val total = state.logs.sumOf { it.seconds } / 60
                if (total > 0) Text("Total focused time logged: ${Times.duration(total.toInt())}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (byDay.isEmpty()) {
            item { SectionCard("No time logged yet", subtitle = "Press ▶ on a task in Today to start its timer.") {} }
        }
        byDay.forEach { (day, logs) ->
            item(key = day.toString()) {
                val sum = logs.sumOf { it.seconds }
                SectionCard(prettyDate(day), subtitle = "${Times.duration((sum / 60).toInt())} logged") {
                    logs.forEach { l ->
                        val start = Instant.ofEpochMilli(l.startEpochMs).atZone(zone).toLocalTime()
                        val actualMin = (l.seconds / 60.0).roundToInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d:%02d".format(start.hour, start.minute), Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
                            Column(Modifier.weight(1f)) {
                                Text(l.title, style = MaterialTheme.typography.bodyMedium)
                                Text("planned ${Times.duration(l.plannedMinutes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                clock(l.seconds),
                                fontWeight = FontWeight.SemiBold,
                                color = if (actualMin > l.plannedMinutes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
