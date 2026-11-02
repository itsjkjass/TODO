package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.GradientHero
import com.pacemate.app.ui.PaceIcons
import androidx.compose.foundation.layout.size
import com.pacemate.app.ui.PaceCard
import com.pacemate.app.ui.ScreenHeader
import com.pacemate.app.ui.SectionCard
import com.pacemate.app.ui.SectionLabel
import com.pacemate.app.ui.theme.Space

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var reminders by remember { mutableStateOf(vm.settings.startReminders) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        ScreenHeader(title = "Settings", eyebrow = "PaceMate", subtitle = "Changes to your day and preferences apply when you re-plan.")

        Button(
            onClick = { vm.replan(showMessage = true) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(Space.sm))
            Text("Save & re-plan")
        }

        SectionLabel("Assistant")
        AiSettingsCard(vm, autoSave = false)

        SectionLabel("Notifications")
        PaceCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = reminders, role = Role.Switch) { reminders = it; vm.setReminders(it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Heads-up before each block", style = MaterialTheme.typography.bodyLarge)
                    Text("A reminder 2 minutes before a planned task starts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The whole row is the toggle; the switch only shows its state.
                Switch(checked = reminders, onCheckedChange = null)
            }
            Text(
                "The \"time's up\" alert for a running timer is always on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("Your day")
        GradientHero(Modifier.fillMaxWidth(), onClick = { vm.openChat() }) {
            Row(Modifier.padding(Space.xl), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Talk me through your day", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Changed jobs, new gym slot, different bedtime? Just chat and I'll update your routine.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(PaceIcons.Chat, contentDescription = null, modifier = Modifier.padding(start = Space.md).size(32.dp))
            }
        }
        Text(
            "Or adjust the details by hand:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )
        RoutineEditor(state.profile.routine) { r -> vm.updateProfile { it.copy(routine = r) } }

        SectionLabel("Preferences")
        PreferencesEditor(state.profile.preferences) { p -> vm.updateProfile { it.copy(preferences = p) } }

        SectionLabel("Data")
        SectionCard("Start over", subtitle = "Deletes your routine, goals, plan and time log from this phone.") {
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete all data") }
        }
        Spacer(Modifier.height(Space.xxl))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete everything?") },
            text = { Text("Your routine, goals, plan and time log will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.resetEverything(); confirmReset = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
