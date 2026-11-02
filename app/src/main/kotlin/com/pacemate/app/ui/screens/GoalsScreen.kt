package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.SectionCard
import com.pacemate.app.ui.prettyDate
import com.pacemate.core.Goal
import com.pacemate.core.GoalStatus
import com.pacemate.core.SessionStatus
import com.pacemate.core.StateOps
import com.pacemate.core.Times
import java.time.LocalDate

@Composable
fun GoalsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Goal?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        if (adding || editing != null) {
            GoalEditor(vm, editing) { adding = false; editing = null }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Goals", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Text("New goal") }
        }
        val active = state.goals.filter { it.status == GoalStatus.ACTIVE }.sortedBy { it.deadline }
        val finished = state.goals.filter { it.status != GoalStatus.ACTIVE }
        if (active.isEmpty()) {
            SectionCard("No active goals", subtitle = "Add something you'd like to get done and I'll plan it for you.") {}
        }
        active.forEach { g -> GoalCard(vm, g, state.sessions.filter { it.goalId == g.id }, onEdit = { editing = g }, onDelete = { confirmDelete = g }) }
        if (finished.isNotEmpty()) {
            Text("Completed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            finished.forEach { g -> GoalCard(vm, g, emptyList(), onEdit = { editing = g }, onDelete = { confirmDelete = g }) }
        }
        Spacer(Modifier.height(24.dp))
    }

    confirmDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${g.title}\"?") },
            text = { Text("Its planned tasks are removed. Time already logged stays in your log.") },
            confirmButton = { TextButton(onClick = { vm.deleteGoal(g.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GoalCard(vm: AppViewModel, g: Goal, sessions: List<com.pacemate.core.PlannedSession>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val state by vm.state.collectAsState()
    val warning = state.planWarnings.firstOrNull { it.goalId == g.id }
    val lastSession = sessions.filter { it.status == SessionStatus.PLANNED }.maxByOrNull { it.date }
    SectionCard(
        g.title,
        subtitle = "Due ${prettyDate(LocalDate.parse(g.deadline))}" +
            (lastSession?.let { " · on track to finish ${prettyDate(LocalDate.parse(it.date))}" } ?: "") +
            " · ${listOf("", "high", "normal", "low")[g.priority.coerceIn(1, 3)]} priority",
    ) {
        LinearProgressIndicator(progress = { StateOps.goalProgress(state, g) }, modifier = Modifier.fillMaxWidth())
        warning?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (g.planNote.isNotBlank()) Text(g.planNote, style = MaterialTheme.typography.bodySmall)
        g.subtasks.forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = st.completed,
                    onCheckedChange = { checked -> if (checked) vm.completeSubtask(g.id, st.id) else vm.reopenSubtask(g.id, st.id) },
                )
                Column(Modifier.weight(1f)) {
                    Text(st.title, style = MaterialTheme.typography.bodyMedium)
                    val logged = state.logs.filter { it.subtaskId == st.id }.sumOf { it.seconds } / 60
                    Text(
                        "~${Times.duration(st.estimateMinutes)} typical" + if (logged > 0) " · you've logged ${Times.duration(logged.toInt())}" else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit goal") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete goal") }
        }
    }
}
