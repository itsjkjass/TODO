package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.ChoiceChips
import com.pacemate.app.ui.DateField
import com.pacemate.app.ui.SectionCard
import com.pacemate.core.Goal
import com.pacemate.core.GoalRequest
import com.pacemate.core.PaceModel
import com.pacemate.core.Subtask
import com.pacemate.core.Times
import java.time.LocalDate
import java.util.UUID

private data class StepDraft(
    val id: String,
    val title: String,
    val minutes: String,
    val difficulty: Int,
    val completed: Boolean = false,
    val notes: String = "",
)

/**
 * Create or edit a goal: describe it, let the AI break it into steps,
 * adjust the steps, then save (which re-plans the calendar).
 */
@Composable
fun GoalEditor(vm: AppViewModel, existing: Goal?, onClose: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val hasKey by vm.apiKeySet.collectAsState()
    val state by vm.state.collectAsState()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var details by remember { mutableStateOf(existing?.details ?: "") }
    var deadline by remember { mutableStateOf(existing?.deadline?.let { LocalDate.parse(it) } ?: LocalDate.now().plusDays(14)) }
    var priority by remember { mutableStateOf(existing?.priority ?: 2) }
    var estimateHours by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(existing?.category ?: "general") }
    var note by remember { mutableStateOf(existing?.planNote ?: "") }
    val steps = remember {
        mutableStateListOf<StepDraft>().apply {
            existing?.subtasks?.forEach { add(StepDraft(it.id, it.title, it.estimateMinutes.toString(), it.difficulty, it.completed, it.notes)) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionCard("About the goal", subtitle = "Something you want to get done in the near future.") {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("What do you want to achieve?") }, singleLine = true)
            OutlinedTextField(
                details, { details = it }, Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("Details (optional)") },
                placeholder = { Text("e.g. 3,000-word essay on climate policy, need 5 sources") },
            )
            DateField("Deadline", deadline) { deadline = it }
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(1 to "High", 2 to "Normal", 3 to "Low"), priority) { priority = it }
            OutlinedTextField(
                estimateHours, { estimateHours = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(),
                label = { Text("Your guess of total hours (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Button(
                onClick = {
                    vm.breakdownGoal(GoalRequest(title.trim(), details.trim(), deadline.toString(), estimateHours.toDoubleOrNull())) { r ->
                        steps.removeAll { !it.completed }
                        r.subtasks.forEach { steps.add(StepDraft(UUID.randomUUID().toString(), it.title, it.estimateMinutes.toString(), it.difficulty, notes = it.notes)) }
                        category = r.category
                        note = r.note
                    }
                },
                enabled = title.isNotBlank() && busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (hasKey) "Break it down with AI" else "Break it down (offline)") }
        }

        if (steps.isNotEmpty() || note.isNotBlank()) {
            val totalAvg = steps.filter { !it.completed }.sumOf { it.minutes.toIntOrNull() ?: 0 }
            val realistic = steps.filter { !it.completed }.sumOf {
                PaceModel.realisticMinutes(it.minutes.toIntOrNull() ?: 0, it.difficulty, state.pace, category)
            }
            SectionCard(
                "Steps",
                subtitle = "Typical estimate ${Times.duration(totalAvg)} · at your pace about ${Times.duration(realistic)}" +
                    if (category != "general") " · $category" else "",
            ) {
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
                steps.forEachIndexed { i, s ->
                    StepRow(
                        s,
                        onChange = { steps[i] = it },
                        onDelete = { steps.removeAt(i) },
                    )
                }
                TextButton(onClick = { steps.add(StepDraft(UUID.randomUUID().toString(), "", "30", 2)) }) {
                    Icon(Icons.Default.Add, null); Text("Add step")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    val subtasks = steps.filter { it.title.isNotBlank() }.map {
                        Subtask(it.id, it.title.trim(), (it.minutes.toIntOrNull() ?: 30).coerceIn(5, 600), it.difficulty, it.notes, it.completed)
                    }
                    val goal = (existing ?: Goal(id = UUID.randomUUID().toString(), title = "", deadline = "", createdAtEpochMs = System.currentTimeMillis()))
                        .copy(
                            title = title.trim(), details = details.trim(), deadline = deadline.toString(),
                            priority = priority, category = category, subtasks = subtasks, planNote = note,
                            status = if (subtasks.isNotEmpty() && subtasks.all { it.completed }) com.pacemate.core.GoalStatus.COMPLETED
                            else com.pacemate.core.GoalStatus.ACTIVE,
                        )
                    vm.saveGoal(goal)
                    onClose()
                },
                enabled = title.isNotBlank() && steps.any { it.title.isNotBlank() } && busy == null,
                modifier = Modifier.weight(1f),
            ) { Text("Save & plan") }
        }
    }
}

@Composable
private fun StepRow(s: StepDraft, onChange: (StepDraft) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.completed, onCheckedChange = { onChange(s.copy(completed = it)) })
            OutlinedTextField(s.title, { onChange(s.copy(title = it)) }, Modifier.weight(1f), label = { Text("Step") })
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete step") }
        }
        Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                s.minutes, { onChange(s.copy(minutes = it.filter(Char::isDigit).take(3))) }, Modifier.width(110.dp),
                label = { Text("Minutes") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text("Effort", style = MaterialTheme.typography.labelSmall)
                ChoiceChips(listOf(1 to "Light", 2 to "Normal", 3 to "Deep"), s.difficulty) { onChange(s.copy(difficulty = it)) }
            }
        }
    }
}
