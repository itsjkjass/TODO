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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.SectionCard
import com.pacemate.app.ui.prettyDate
import com.pacemate.core.Goal
import com.pacemate.core.PaceModel
import com.pacemate.core.Times
import java.time.LocalDate

private val STEP_TITLES = listOf("Welcome", "Your day", "Preferences", "Your goals", "Your plan")

/**
 * First-run flow: asks for the daily routine, working hours, preferences
 * and upcoming goals, then builds the first plan.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Step ${step + 1} of ${STEP_TITLES.size} · ${STEP_TITLES[step]}", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { (step + 1f) / STEP_TITLES.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            when (step) {
                0 -> WelcomeStep(vm)
                1 -> DayStep(vm)
                2 -> PreferencesStep(vm)
                3 -> GoalsStep(vm)
                else -> PlanPreviewStep(vm)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            if (step < STEP_TITLES.lastIndex) {
                Button(onClick = { step++ }, modifier = Modifier.weight(1f)) { Text("Next") }
            } else {
                Button(onClick = { vm.finishOnboarding() }, modifier = Modifier.weight(1f)) { Text("Start my plan") }
            }
        }
    }
}

@Composable
private fun WelcomeStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }

    Text("Hi! I'm PaceMate.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(
        "I turn the things you want to get done into small daily tasks that fit around your life - " +
            "planned at your real speed, never more than you're comfortable with, and finished before the deadline. " +
            "A timer tracks each task and tells you when its time is up.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(12.dp))
    SectionCard("What should I call you?") {
        OutlinedTextField(state.profile.name, { n -> vm.updateProfile { it.copy(name = n) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
    }
    SectionCard(
        "AI planning (optional)",
        subtitle = "Paste an Anthropic API key to let Claude read your routine and break goals into realistic steps. " +
            "Without a key the app still works with a simpler offline planner. The key stays on this phone.",
    ) {
        OutlinedTextField(
            key, { key = it; vm.saveApiKey(it, model) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Anthropic API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(model, { model = it; vm.saveApiKey(key, it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Model") })
    }
}

@Composable
private fun DayStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    Text("Tell me about your daily routine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text("Describe it in your own words, set it by hand, or both.", style = MaterialTheme.typography.bodyMedium)
    DescribeDayCard(vm)
    RoutineEditor(state.profile.routine) { r -> vm.updateProfile { it.copy(routine = r) } }
}

@Composable
private fun PreferencesStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    Text("How do you like to work?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    PreferencesEditor(state.profile.preferences) { p -> vm.updateProfile { it.copy(preferences = p) } }
}

@Composable
private fun GoalsStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(state.goals.isEmpty()) }

    Text("What do you want to get done soon?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Add the things you want to do in the near future - an exam, a project, decluttering, a side hustle. " +
            "I'll break each one into steps and spread them over the days before its deadline.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))

    when {
        adding -> GoalEditor(vm, null) { adding = false }
        editing != null -> GoalEditor(vm, editing) { editing = null }
        else -> {
            state.goals.forEach { g ->
                GoalSummaryCard(g, onEdit = { editing = g }, onDelete = { vm.deleteGoal(g.id) })
            }
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Text(if (state.goals.isEmpty()) "Add a goal" else "Add another goal")
            }
            if (state.goals.isEmpty()) {
                Text("You can also skip this and add goals later.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun GoalSummaryCard(g: Goal, onEdit: () -> Unit, onDelete: () -> Unit) {
    SectionCard(g.title, subtitle = "Due ${prettyDate(LocalDate.parse(g.deadline))} · ${g.subtasks.size} steps · ~${Times.duration(g.subtasks.sumOf { it.estimateMinutes })}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.subtasks.take(3).joinToString("\n") { "• ${it.title}" } + if (g.subtasks.size > 3) "\n…" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
private fun PlanPreviewStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.previewPlan() }
    val p = state.profile.preferences
    Text("Here's how I'll plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    SectionCard("Your pace") {
        Text(PaceModel.describe(PaceModel.initialStats(p.selfRatedPace)))
        Text(
            "Every estimate gets a ${(PaceModel.PLANNING_BUFFER * 100).toInt()}% cushion and ${PaceModel.WARMUP_MINUTES} minutes to get started, " +
                "sessions are at most ${p.focusBlockMinutes} min with ${p.breakMinutes}-min breaks, " +
                "and you'll never get more than ${hoursText(p.maxTaskHoursWorkday)} on work days or ${hoursText(p.maxTaskHoursFreeDay)} on free days.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val upcoming = state.sessions.groupBy { it.date }.entries.sortedBy { it.key }.take(5)
    if (upcoming.isEmpty()) {
        SectionCard("Nothing planned yet", subtitle = "Add goals now or later from the Goals tab.") {}
    } else {
        SectionCard("First days") {
            upcoming.forEach { (date, list) ->
                Text("${prettyDate(LocalDate.parse(date))} · ${Times.duration(list.sumOf { it.durationMinutes })}", fontWeight = FontWeight.SemiBold)
                list.forEach { s -> Text("  ${Times.format(s.startMinute)}  ${s.title}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    state.planWarnings.forEach { w ->
        SectionCard("Heads-up: ${w.goalTitle}") { Text(w.message, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun hoursText(h: Double) = Times.duration((h * 60).toInt())
