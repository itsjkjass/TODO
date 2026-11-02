package com.pacemate.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.Eyebrow
import com.pacemate.app.ui.PaceCard
import com.pacemate.app.ui.ProgressRing
import com.pacemate.app.ui.SectionCard
import com.pacemate.app.ui.StatusPill
import com.pacemate.app.ui.prettyDate
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import com.pacemate.app.ui.theme.TabularNumbers
import com.pacemate.core.Goal
import com.pacemate.core.PaceModel
import com.pacemate.core.Times
import java.time.LocalDate

private val STEP_TITLES = listOf("Welcome", "Your day", "How you work", "Your goals", "Your plan")

/**
 * First-run flow: asks for the daily routine, working hours, preferences
 * and upcoming goals, then builds the first plan.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Segmented progress: one bar per step.
        Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.lg)) {
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = "Step ${step + 1} of ${STEP_TITLES.size}: ${STEP_TITLES[step]}" },
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                STEP_TITLES.indices.forEach { i ->
                    val color by animateColorAsState(
                        if (i <= step) MaterialTheme.colorScheme.primary else PaceTheme.colors.track,
                        tween(Motion.MEDIUM), label = "seg",
                    )
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
                }
            }
            Spacer(Modifier.height(Space.sm))
            Eyebrow("Step ${step + 1} of ${STEP_TITLES.size} · ${STEP_TITLES[step]}")
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { it / 6 * dir } + fadeIn(tween(Motion.MEDIUM)))
                    .togetherWith(slideOutHorizontally(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) { -it / 6 * dir } + fadeOut(tween(Motion.SHORT)))
            },
            label = "onboarding",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                when (s) {
                    0 -> WelcomeStep(vm)
                    1 -> DayStep(vm)
                    2 -> PreferencesStep(vm)
                    3 -> GoalsStep(vm)
                    else -> PlanPreviewStep(vm)
                }
            }
        }

        // Fixed bottom action bar.
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Back") }
                    }
                    if (step < STEP_TITLES.lastIndex) {
                        Button(onClick = { step++ }, modifier = Modifier.weight(if (step == 0) 1f else 1.6f).height(52.dp)) {
                            Text(if (step == 0) "Get started" else "Continue")
                        }
                    } else {
                        Button(onClick = { vm.finishOnboarding() }, modifier = Modifier.weight(1.6f).height(52.dp)) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(Modifier.width(Space.sm))
                            Text("Start my plan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(bottom = Space.sm)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.WelcomeStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxWidth().padding(vertical = Space.lg), contentAlignment = Alignment.Center) {
        ProgressRing(progress = 0.72f, size = 132.dp, stroke = 12.dp) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        }
    }
    Text("Plan at your pace.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "PaceMate turns the things you want to get done into small daily steps that fit around your life. " +
            "It plans at your real speed, never piles on more than you're comfortable with, and gets you there before the deadline.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(vertical = Space.sm)) {
        Bullet("Learns how long things really take you")
        Bullet("Spreads work evenly so no day is overloaded")
        Bullet("Times every task and nudges you when it's up")
    }
    SectionCard("What should I call you?") {
        OutlinedTextField(
            state.profile.name, { n -> vm.updateProfile { it.copy(name = n) } }, Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("First name") }, shape = MaterialTheme.shapes.medium,
        )
    }
    AiSettingsCard(vm, autoSave = true)
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DayStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    StepTitle("Your daily routine", "Describe it in your own words and let the AI fill it in, set it by hand, or both.")
    DescribeDayCard(vm)
    RoutineEditor(state.profile.routine) { r -> vm.updateProfile { it.copy(routine = r) } }
}

@Composable
private fun PreferencesStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    StepTitle("How do you like to work?", "These shape the size of each block and how full a day can get.")
    PreferencesEditor(state.profile.preferences) { p -> vm.updateProfile { it.copy(preferences = p) } }
}

@Composable
private fun GoalsStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(state.goals.isEmpty()) }

    StepTitle(
        "What do you want to get done?",
        "An exam, a project, decluttering, a side hustle. Each goal is broken into steps and spread over the days before its deadline.",
    )

    when {
        adding -> GoalEditor(vm, null) { adding = false }
        editing != null -> GoalEditor(vm, editing) { editing = null }
        else -> {
            state.goals.forEach { g ->
                GoalSummaryCard(g, onEdit = { editing = g }, onDelete = { vm.deleteGoal(g.id) })
            }
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(Space.sm))
                Text(if (state.goals.isEmpty()) "Add a goal" else "Add another goal")
            }
            if (state.goals.isEmpty()) {
                Text("You can skip this and add goals later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GoalSummaryCard(g: Goal, onEdit: () -> Unit, onDelete: () -> Unit) {
    PaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Due ${prettyDate(LocalDate.parse(g.deadline))} · ${g.subtasks.size} steps · ~${Times.duration(g.subtasks.sumOf { it.estimateMinutes })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${g.title}") }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete ${g.title}") }
        }
        g.subtasks.take(3).forEach {
            Text("•  ${it.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (g.subtasks.size > 3) {
            Text("+ ${g.subtasks.size - 3} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanPreviewStep(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.previewPlan() }
    val p = state.profile.preferences
    StepTitle("Here's your plan", "You can change anything later. The plan adjusts itself as you go.")

    PaceCard(container = MaterialTheme.colorScheme.primaryContainer, border = false) {
        Eyebrow("Your pace", color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(PaceModel.describe(PaceModel.initialStats(p.selfRatedPace)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            "Every estimate gets a ${(PaceModel.PLANNING_BUFFER * 100).toInt()}% cushion and ${PaceModel.WARMUP_MINUTES} minutes to settle in. " +
                "Blocks are at most ${p.focusBlockMinutes} min with ${p.breakMinutes}-min breaks, " +
                "and never more than ${hoursText(p.maxTaskHoursWorkday)} on work days or ${hoursText(p.maxTaskHoursFreeDay)} on free days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
    }

    val upcoming = state.sessions.groupBy { it.date }.entries.sortedBy { it.key }.take(5)
    if (upcoming.isEmpty()) {
        PaceCard {
            Text("Nothing planned yet", style = MaterialTheme.typography.titleMedium)
            Text("Add goals now, or later from the Goals tab.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        upcoming.forEach { (date, list) ->
            PaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prettyDate(LocalDate.parse(date)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusPill(Times.duration(list.sumOf { it.durationMinutes }))
                }
                list.forEach { s ->
                    Row {
                        Text(Times.format(s.startMinute), Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    state.planWarnings.forEach { w ->
        Surface(shape = MaterialTheme.shapes.large, color = PaceTheme.colors.warningContainer, contentColor = PaceTheme.colors.onWarningContainer) {
            Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Heads-up: ${w.goalTitle}", style = MaterialTheme.typography.titleSmall)
                Text(w.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun hoursText(h: Double) = Times.duration((h * 60).toInt())
