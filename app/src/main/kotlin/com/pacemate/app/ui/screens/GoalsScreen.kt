package com.pacemate.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.pacemate.app.ui.staggerIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.EmptyState
import com.pacemate.app.ui.PaceCard
import com.pacemate.app.ui.ProgressRing
import com.pacemate.app.ui.ScreenHeader
import com.pacemate.app.ui.SectionLabel
import com.pacemate.app.ui.StatusPill
import com.pacemate.app.ui.prettyDate
import com.pacemate.app.ui.relativeDays
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import com.pacemate.app.ui.theme.TabularNumbers
import com.pacemate.core.AppState
import com.pacemate.core.Goal
import com.pacemate.core.GoalStatus
import com.pacemate.core.SessionStatus
import com.pacemate.core.StateOps
import com.pacemate.core.Times
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Goal?>(null) }

    val editing = editingId?.let { id -> state.goals.firstOrNull { it.id == id } }
    if (adding || editing != null) {
        GoalEditorPage(vm, editing) { adding = false; editingId = null }
        return
    }

    val active = state.goals.filter { it.status == GoalStatus.ACTIVE }.sortedBy { it.deadline }
    val finished = state.goals.filter { it.status != GoalStatus.ACTIVE }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item {
                ScreenHeader(
                    title = "Goals",
                    eyebrow = if (active.isEmpty()) "Nothing in progress" else "${active.size} in progress",
                    subtitle = "Each goal is broken into steps and paced to finish before its deadline.",
                )
            }
            if (active.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Star,
                        title = "What do you want to get done?",
                        body = "An exam, a project, a side hustle. Add it and I'll plan small daily steps that fit your life.",
                    )
                }
            }
            itemsIndexed(active, key = { _, g -> g.id }) { i, g ->
                Box(Modifier.animateItem().staggerIn(i)) {
                    GoalCard(vm, state, g, onEdit = { editingId = g.id }, onDelete = { confirmDelete = g })
                }
            }
            if (finished.isNotEmpty()) {
                item { SectionLabel("Completed") }
                items(finished, key = { it.id }) { g ->
                    GoalCard(vm, state, g, onEdit = { editingId = g.id }, onDelete = { confirmDelete = g })
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New goal") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Space.lg),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }

    confirmDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${g.title}\"?") },
            text = { Text("Its planned blocks are removed. Time you've already logged stays in your log.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteGoal(g.id); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GoalEditorPage(vm: AppViewModel, existing: Goal?, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
        ScreenHeader(
            title = if (existing == null) "New goal" else "Edit goal",
            eyebrow = "Goals",
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close without saving") }
        }
        GoalEditor(vm, existing, onClose)
        Spacer(Modifier.height(Space.xxl))
    }
}

@Composable
private fun GoalCard(vm: AppViewModel, state: AppState, g: Goal, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable(g.id) { mutableStateOf(false) }
    val warning = state.planWarnings.firstOrNull { it.goalId == g.id }
    val progress = StateOps.goalProgress(state, g)
    val deadline = LocalDate.parse(g.deadline)
    val finishing = state.sessions.filter { it.goalId == g.id && it.status == SessionStatus.PLANNED }.maxByOrNull { it.date }
    val next = g.subtasks.firstOrNull { !it.completed }
    val completed = g.status != GoalStatus.ACTIVE
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.MEDIUM, easing = Motion.Emphasized), label = "chevron")

    PaceCard(modifier = Modifier.animateContentSize(tween(Motion.MEDIUM, easing = Motion.Emphasized)), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            ProgressRing(
                progress = progress,
                size = 56.dp,
                stroke = 6.dp,
                color = if (completed) PaceTheme.colors.success else MaterialTheme.colorScheme.primary,
                description = "${(progress * 100).roundToInt()} percent complete",
            ) {
                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium.merge(TabularNumbers))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Due ${prettyDate(deadline)} · ${g.subtasks.count { it.completed }}/${g.subtasks.size} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GoalMenu(onEdit, onDelete, g.title)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            when {
                completed -> StatusPill("Completed", container = PaceTheme.colors.success.copy(alpha = 0.14f), content = PaceTheme.colors.success)
                warning != null -> StatusPill("At risk", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
                else -> StatusPill("On track", container = PaceTheme.colors.calm.copy(alpha = 0.14f), content = PaceTheme.colors.calm)
            }
            if (!completed) {
                StatusPill(relativeDays(deadline), container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (g.priority == 1 && !completed) {
                StatusPill("High priority", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        if (!completed) {
            if (warning != null) {
                Text(warning.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (finishing != null) {
                Text(
                    "On pace to finish ${prettyDate(LocalDate.parse(finishing.date))}" + (next?.let { ". Next: ${it.title}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "Hide steps" else "Show ${g.subtasks.size} steps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.KeyboardArrowDown, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.rotate(chevron),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)),
            exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)),
        ) {
            Column {
                if (g.planNote.isNotBlank()) {
                    Text(g.planNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Space.sm))
                }
                g.subtasks.forEach { st ->
                    val logged = state.logs.filter { it.subtaskId == st.id }.sumOf { it.seconds } / 60
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = st.completed,
                            onCheckedChange = { checked -> if (checked) vm.completeSubtask(g.id, st.id) else vm.reopenSubtask(g.id, st.id) },
                            modifier = Modifier.semantics { contentDescription = st.title },
                        )
                        Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
                            Text(
                                st.title,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (st.completed) TextDecoration.LineThrough else null,
                                color = if (st.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "~${Times.duration(st.estimateMinutes)} typical" + if (logged > 0) " · ${Times.duration(logged.toInt())} logged" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (st.difficulty >= 3 && !st.completed) {
                            StatusPill("Deep", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalMenu(onEdit: () -> Unit, onDelete: () -> Unit, title: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for $title") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Edit goal") }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text("Delete goal", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; onDelete() })
        }
    }
}
