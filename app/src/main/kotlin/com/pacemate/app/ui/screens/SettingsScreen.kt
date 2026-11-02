package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.SectionCard

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }
    var reminders by remember { mutableStateOf(vm.settings.startReminders) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Changes to your routine and preferences are applied when you tap \"Save & re-plan\".",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.replan(showMessage = true) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Save & re-plan")
        }

        SectionCard("AI (Claude)", subtitle = "Used to read your routine and break goals into realistic steps.") {
            OutlinedTextField(
                key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Anthropic API key") }, visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Model") })
            OutlinedButton(onClick = { vm.saveApiKey(key, model); vm.toast("AI settings saved") }) { Text("Save AI settings") }
        }

        SectionCard("Notifications") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remind me 2 minutes before each task", Modifier.weight(1f))
                Switch(reminders, { reminders = it; vm.setReminders(it) })
            }
            Text(
                "The \"time's up\" alert for a running task timer is always on.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("Your day", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        DescribeDayCard(vm)
        RoutineEditor(state.profile.routine) { r -> vm.updateProfile { it.copy(routine = r) } }

        Text("Preferences", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        PreferencesEditor(state.profile.preferences) { p -> vm.updateProfile { it.copy(preferences = p) } }

        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Text("Start over (delete all data)")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete everything?") },
            text = { Text("Your routine, goals, plan and time log will be deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.resetEverything(); confirmReset = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
