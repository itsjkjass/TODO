package com.pacemate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.ChoiceChips
import com.pacemate.app.ui.DayChips
import com.pacemate.app.ui.LabeledSlider
import com.pacemate.app.ui.SectionCard
import com.pacemate.app.ui.TimeField
import com.pacemate.app.ui.hoursLabel
import com.pacemate.core.EnergyPeak
import com.pacemate.core.Preferences
import com.pacemate.core.Routine
import com.pacemate.core.SelfRatedPace
import com.pacemate.core.TimeBlock
import com.pacemate.core.Times
import kotlin.math.roundToInt

/** "Describe your day in your own words" box that the AI turns into routine fields. */
@Composable
fun DescribeDayCard(vm: AppViewModel) {
    val busy by vm.busy.collectAsState()
    val hasKey by vm.apiKeySet.collectAsState()
    var text by remember { mutableStateOf("") }
    SectionCard(
        "Describe your typical day",
        subtitle = "In your own words: when you wake up and sleep, work or classes, meals, gym, family time, " +
            "when you have the most energy and how much time you'd like to spend on your own tasks.",
    ) {
        OutlinedTextField(
            text, { text = it }, Modifier.fillMaxWidth(), minLines = 4,
            placeholder = { Text("e.g. I get up at 6:30, work 9 to 5:30 Mon-Fri with a 30 min commute, gym Tue/Thu at 7pm. I'm sharpest in the morning and can do about 1.5 hours of my own stuff on weekdays, more on weekends.") },
        )
        Button(onClick = { vm.fillRoutineFromText(text) }, enabled = busy == null && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (hasKey) "Fill in my routine with AI" else "Needs an API key (or fill in below)")
        }
    }
}

@Composable
fun RoutineEditor(routine: Routine, onChange: (Routine) -> Unit) {
    SectionCard("Sleep") {
        TimeField("I usually wake up at", routine.wakeTime) { onChange(routine.copy(wakeTime = it)) }
        TimeField("I usually go to sleep at", routine.sleepTime) { onChange(routine.copy(sleepTime = it)) }
    }

    val hasWork = routine.workDays.isNotEmpty()
    SectionCard("Work / school hours", subtitle = "Tasks are never planned inside these hours.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("I have fixed working hours", Modifier.weight(1f))
            Switch(hasWork, { on -> onChange(routine.copy(workDays = if (on) setOf(1, 2, 3, 4, 5) else emptySet())) })
        }
        if (hasWork) {
            TimeField("Start", routine.workStart) { onChange(routine.copy(workStart = it)) }
            TimeField("End", routine.workEnd) { onChange(routine.copy(workEnd = it)) }
            Text("Work days", style = MaterialTheme.typography.labelLarge)
            DayChips(routine.workDays) { onChange(routine.copy(workDays = it)) }
            LabeledSlider(
                "Commute (one way)", routine.commuteMinutes.toFloat(), 0f..120f, 23,
                { "${it.roundToInt()} min" },
            ) { onChange(routine.copy(commuteMinutes = (it / 5).roundToInt() * 5)) }
        }
    }

    BlockListEditor(
        title = "Meals",
        subtitle = "Kept free every day.",
        blocks = routine.meals,
        showDays = false,
        newBlock = { TimeBlock("Meal", 12 * 60, 30) },
        onChange = { onChange(routine.copy(meals = it)) },
    )
    BlockListEditor(
        title = "Other fixed commitments",
        subtitle = "Gym, classes, school run, family time… anything that repeats.",
        blocks = routine.fixedBlocks,
        showDays = true,
        newBlock = { TimeBlock("Commitment", 18 * 60, 60) },
        onChange = { onChange(routine.copy(fixedBlocks = it)) },
    )
}

@Composable
private fun BlockListEditor(
    title: String,
    subtitle: String,
    blocks: List<TimeBlock>,
    showDays: Boolean,
    newBlock: () -> TimeBlock,
    onChange: (List<TimeBlock>) -> Unit,
) {
    SectionCard(title, subtitle = subtitle) {
        blocks.forEachIndexed { i, b ->
            fun set(nb: TimeBlock) = onChange(blocks.toMutableList().also { it[i] = nb })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(b.label, { set(b.copy(label = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Name") })
                    IconButton(onClick = { onChange(blocks.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Delete, "Remove") }
                }
                TimeField("Starts", b.start) { set(b.copy(start = it)) }
                LabeledSlider("Duration", b.durationMinutes.toFloat(), 15f..240f, 14, { Times.duration(it.roundToInt()) }) {
                    set(b.copy(durationMinutes = (it / 15).roundToInt() * 15))
                }
                if (showDays) {
                    Text(if (b.days.isEmpty()) "Every day (tap to choose days)" else "On", style = MaterialTheme.typography.labelSmall)
                    DayChips(b.days) { set(b.copy(days = it)) }
                }
                if (i < blocks.lastIndex) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
        TextButton(onClick = { onChange(blocks + newBlock()) }) { Icon(Icons.Default.Add, null); Text("Add") }
    }
}

@Composable
fun PreferencesEditor(prefs: Preferences, onChange: (Preferences) -> Unit) {
    SectionCard("How you like to work") {
        LabeledSlider("Longest focus session", prefs.focusBlockMinutes.toFloat(), 15f..120f, 20, { "${it.roundToInt()} min" }) {
            onChange(prefs.copy(focusBlockMinutes = (it / 5).roundToInt() * 5))
        }
        LabeledSlider("Break between sessions", prefs.breakMinutes.toFloat(), 0f..30f, 5, { "${it.roundToInt()} min" }) {
            onChange(prefs.copy(breakMinutes = (it / 5).roundToInt() * 5))
        }
        Text("When do you have the most energy?", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            listOf(EnergyPeak.MORNING to "Morning", EnergyPeak.AFTERNOON to "Afternoon", EnergyPeak.EVENING to "Evening"),
            prefs.peakEnergy,
        ) { onChange(prefs.copy(peakEnergy = it)) }
        Text("How fast do you usually work?", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(SelfRatedPace.entries.map { it to it.label }, prefs.selfRatedPace) { onChange(prefs.copy(selfRatedPace = it)) }
        Text(
            "This is a starting point. The timer learns your real speed from every task you log.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Keep it light", subtitle = "PaceMate never plans more than this, so deadlines are met without stress.") {
        LabeledSlider("Task time on work days", prefs.maxTaskHoursWorkday.toFloat(), 0f..8f, 31, ::hoursLabel) {
            onChange(prefs.copy(maxTaskHoursWorkday = (it * 4).roundToInt() / 4.0))
        }
        LabeledSlider("Task time on free days", prefs.maxTaskHoursFreeDay.toFloat(), 0f..12f, 47, ::hoursLabel) {
            onChange(prefs.copy(maxTaskHoursFreeDay = (it * 4).roundToInt() / 4.0))
        }
        LabeledSlider("Free time after waking up", prefs.morningBufferMinutes.toFloat(), 0f..120f, 23, { "${it.roundToInt()} min" }) {
            onChange(prefs.copy(morningBufferMinutes = (it / 5).roundToInt() * 5))
        }
        LabeledSlider("Wind-down before sleep", prefs.windDownMinutes.toFloat(), 0f..180f, 11, { "${it.roundToInt()} min" }) {
            onChange(prefs.copy(windDownMinutes = (it / 15).roundToInt() * 15))
        }
        LabeledSlider("Finish before deadlines by", prefs.deadlineBufferDays.toFloat(), 0f..7f, 6, { "${it.roundToInt()} day(s)" }) {
            onChange(prefs.copy(deadlineBufferDays = it.roundToInt()))
        }
        Text("Rest days (nothing planned unless a deadline is close)", style = MaterialTheme.typography.labelLarge)
        DayChips(prefs.restDays) { onChange(prefs.copy(restDays = it)) }
    }

    SectionCard("Anything else the AI should know?") {
        OutlinedTextField(
            prefs.notes, { onChange(prefs.copy(notes = it)) }, Modifier.fillMaxWidth(), minLines = 2,
            placeholder = { Text("e.g. I procrastinate on writing, I prefer short tasks after work, I have ADHD…") },
        )
    }
}
