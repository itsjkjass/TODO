package com.pacemate.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pacemate.app.ui.AppViewModel
import com.pacemate.app.ui.BrandAvatar
import com.pacemate.app.ui.Eyebrow
import com.pacemate.app.ui.GradientButton
import com.pacemate.app.ui.TypingDots
import com.pacemate.app.ui.staggerIn
import com.pacemate.app.ui.theme.Motion
import com.pacemate.app.ui.theme.PaceTheme
import com.pacemate.app.ui.theme.Space
import com.pacemate.core.ChatMessage
import com.pacemate.core.Loose
import com.pacemate.core.RoutineDraft
import com.pacemate.core.Times

/** Full-screen chat with a top bar (opened from Today / Settings). */
@Composable
fun ChatScreen(vm: AppViewModel) {
    val chat by vm.chat.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.xs, top = Space.xl, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandAvatar(size = 40.dp)
                Column(Modifier.weight(1f).padding(start = Space.md)) {
                    Text("PaceMate", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            chat.thinking -> "typing…"
                            chat.offline -> "Quick questions · offline"
                            else -> "Your planning coach"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.restartChat() }) { Icon(Icons.Rounded.Refresh, contentDescription = "Start the conversation over") }
                IconButton(onClick = { vm.closeChat() }) { Icon(Icons.Rounded.Close, contentDescription = "Close chat") }
            }
            RoutineChat(vm, Modifier.weight(1f))
        }
    }
}

/**
 * The conversation itself: what's been learned, the messages, suggested answers and
 * the composer. Used full-screen and embedded in onboarding.
 */
@Composable
fun RoutineChat(vm: AppViewModel, modifier: Modifier = Modifier) {
    val chat by vm.chat.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.startChatIfNeeded() }
    LaunchedEffect(chat.messages.size, chat.thinking) {
        val count = chat.messages.size + (if (chat.thinking) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxWidth()) {
        LearnedStrip(chat.learned)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            itemsIndexed(chat.messages, key = { i, _ -> i }) { _, m ->
                Bubble(m, Modifier.animateItem().staggerIn())
            }
            if (chat.thinking) {
                item(key = "typing") {
                    Row(Modifier.animateItem().semantics { contentDescription = "PaceMate is typing" }, verticalAlignment = Alignment.Bottom) {
                        BrandAvatar(size = 28.dp)
                        Surface(
                            modifier = Modifier.padding(start = Space.sm),
                            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                            color = PaceTheme.colors.botBubble,
                        ) { TypingDots(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) }
                    }
                }
            }
            chat.error?.let { err ->
                item(key = "error") {
                    Text(
                        err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 36.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chat.done && !chat.thinking,
            enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)),
            exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT)),
        ) {
            GradientButton(onClick = { vm.acceptChat() }, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.size(Space.sm))
                Text("Looks right, save my routine")
            }
        }

        // Suggested answers
        AnimatedVisibility(
            visible = chat.quickReplies.isNotEmpty() && !chat.thinking,
            enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM)),
            exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT)),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(vertical = Space.xs),
            ) {
                items(chat.quickReplies, key = { it }) { r ->
                    SuggestionChip(
                        onClick = { vm.sendChat(r) },
                        label = { Text(r) },
                        shape = CircleShape,
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = null,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        // Composer
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type your answer…") },
                shape = CircleShape,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.sendChat(input); input = "" }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            Spacer(Modifier.size(Space.sm))
            FilledIconButton(
                onClick = { vm.sendChat(input); input = "" },
                enabled = input.isNotBlank() && !chat.thinking,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Icon(Icons.Rounded.Send, contentDescription = "Send") }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, modifier: Modifier = Modifier) {
    if (m.fromUser) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(Brush.linearGradient(PaceTheme.colors.userBubble))
                    .padding(horizontal = Space.lg, vertical = Space.md),
            ) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge, color = PaceTheme.colors.onGradient)
            }
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            BrandAvatar(size = 28.dp)
            Surface(
                modifier = Modifier.padding(start = Space.sm).widthIn(max = 300.dp),
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                color = PaceTheme.colors.botBubble,
            ) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md))
            }
        }
    }
}

/** Chips for everything learned so far; new ones animate in as the conversation goes. */
@Composable
private fun LearnedStrip(d: RoutineDraft?) {
    val chips = learnedChips(d)
    AnimatedVisibility(
        visible = chips.isNotEmpty(),
        enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM)),
        exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT)),
    ) {
        Column(Modifier.padding(top = Space.xs)) {
            Eyebrow("What I know so far", Modifier.padding(horizontal = Space.lg))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(chips, key = { it.first }) { (label, value) ->
                    Surface(
                        modifier = Modifier.animateItem(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(Modifier.padding(horizontal = Space.md, vertical = Space.xs + 2.dp), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun learnedChips(d: RoutineDraft?): List<Pair<String, String>> {
    if (d == null) return emptyList()
    val out = mutableListOf<Pair<String, String>>()
    d.wakeTime?.let { out += "Wake" to it }
    d.sleepTime?.let { out += "Sleep" to it }
    if (d.workDays != null && d.workDays!!.isEmpty()) out += "Work" to "Flexible"
    if (d.workStart != null && d.workEnd != null) out += "Work" to "${d.workStart}–${d.workEnd}"
    d.workDays?.takeIf { it.isNotEmpty() }?.let { out += "Days" to Loose.daysLabel(it.toSet()) }
    d.commuteMinutes?.takeIf { it > 0 }?.let { out += "Commute" to "$it min" }
    d.meals?.takeIf { it.isNotEmpty() }?.let { out += "Meals" to "${it.size}" }
    d.fixedBlocks?.forEach { out += it.label to it.start }
    d.peakEnergy?.let { out += "Peak" to it.lowercase().replaceFirstChar { c -> c.uppercase() } }
    d.maxTaskHoursWorkday?.let { out += "Work-day tasks" to Times.duration((it * 60).toInt()) }
    d.maxTaskHoursFreeDay?.let { out += "Free-day tasks" to Times.duration((it * 60).toInt()) }
    d.focusBlockMinutes?.let { out += "Focus" to "$it min" }
    return out.distinctBy { it.first }
}
