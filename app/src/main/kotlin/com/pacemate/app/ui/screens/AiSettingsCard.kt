package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.ChoiceChips
import com.pacemate.app.ui.SectionCard
import com.pacemate.core.AiProvider

/**
 * Choose the AI provider (Groq or Kimi), paste its API key and pick a model.
 * With [autoSave] every edit is stored immediately (used in onboarding);
 * otherwise a Save button is shown.
 */
@Composable
fun AiSettingsCard(vm: AppViewModel, autoSave: Boolean) {
    val uri = LocalUriHandler.current
    var provider by remember { mutableStateOf(vm.settings.provider) }
    var key by remember { mutableStateOf(vm.settings.apiKey(provider)) }
    var model by remember { mutableStateOf(vm.settings.model(provider)) }

    fun persist() = vm.saveAi(provider, key, model)

    SectionCard(
        "AI planning (optional)",
        subtitle = "Lets the AI read your routine and break goals into realistic steps. " +
            "Groq has a free tier, so it costs nothing for normal use. Without a key the app still works " +
            "with a simpler offline planner. Your key stays on this phone.",
    ) {
        ChoiceChips(AiProvider.entries.map { it to it.label }, provider) { p ->
            provider = p
            key = vm.settings.apiKey(p)
            model = vm.settings.model(p)
            if (autoSave) persist()
        }
        OutlinedTextField(
            key, { key = it; if (autoSave) persist() }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("${provider.label} API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            model, { model = it; if (autoSave) persist() }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Model") },
            supportingText = { Text(provider.modelHint) },
        )
        OutlinedButton(onClick = { uri.openUri(provider.keyUrl) }) { Text("Get a ${provider.label.substringBefore(" (")} API key") }
        if (!autoSave) {
            OutlinedButton(onClick = { persist(); vm.toast("AI settings saved") }) { Text("Save AI settings") }
        }
        Text(
            "Tip: if planning fails with \"model not found\", copy a model name from your provider's console.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
