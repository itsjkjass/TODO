package com.pacemate.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.pacemate.app.ui.screens.ChatScreen
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.NavigationBarItemDefaults
import com.pacemate.app.ui.screens.MiniTimerBar
import com.pacemate.app.ui.theme.Motion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pacemate.app.timer.Notifications
import com.pacemate.app.ui.screens.GoalsScreen
import com.pacemate.app.ui.screens.LogScreen
import com.pacemate.app.ui.screens.OnboardingScreen
import com.pacemate.app.ui.screens.PlanScreen
import com.pacemate.app.ui.screens.SettingsScreen
import com.pacemate.app.ui.screens.TodayScreen
import com.pacemate.app.ui.theme.PaceMateTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) vm.toast("Without notifications I can't tell you when a task's time is over.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Each time the app comes to the foreground on a new day (or after a while), refresh the plan
        // so missed tasks move forward instead of piling up.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val s = vm.state.value
                val stale = System.currentTimeMillis() - s.lastPlannedEpochMs > 3 * 3600_000L
                val newDay = s.lastPlannedEpochMs > 0 &&
                    java.time.Instant.ofEpochMilli(s.lastPlannedEpochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate() != LocalDate.now()
                if (s.onboarded && (stale || newDay)) vm.replan()
            }
        }
        setContent {
            PaceMateTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(vm)
                }
            }
        }
    }
}

private data class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val TABS = listOf(
    Tab("Today", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("Plan", Icons.Rounded.DateRange, Icons.Outlined.DateRange),
    Tab("Goals", Icons.Rounded.Star, Icons.Outlined.Star),
    Tab("Log", Icons.Rounded.List, Icons.Outlined.List),
    Tab("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
private fun Root(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val celebrate by vm.celebrate.collectAsState()
    val chatOpen by vm.chatOpen.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    BackHandler(enabled = chatOpen) { vm.closeChat() }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.onboarded) {
                Column {
                    val timer = state.activeTimer
                    val running = timer?.let { t -> state.sessions.firstOrNull { it.id == t.sessionId } }
                    if (timer != null && running != null && tab != 0) {
                        MiniTimerBar(vm, running, timer) { tab = 0 }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        TABS.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                // The visible label names the tab, so the icon is decorative.
                                icon = { Icon(if (tab == i) t.selected else t.unselected, contentDescription = null) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.onboarded) {
                OnboardingScreen(vm)
            } else {
                Crossfade(targetState = tab, animationSpec = tween(Motion.MEDIUM), label = "tabs") { t ->
                    when (t) {
                        0 -> TodayScreen(vm)
                        1 -> PlanScreen(vm)
                        2 -> GoalsScreen(vm)
                        3 -> LogScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }
            busy?.let { text ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 6.dp,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.inversePrimary)
                        Column(Modifier.padding(start = 12.dp)) { Text(text) }
                    }
                }
            }
        }
    }

    // Full-screen routine chat slides up over everything.
    AnimatedVisibility(
        visible = chatOpen,
        enter = slideInVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { it } + fadeIn(tween(Motion.MEDIUM)),
        exit = slideOutVertically(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(Motion.SHORT)),
    ) { ChatScreen(vm) }

    // Celebration burst when a block or step is finished.
    ConfettiBurst(celebrate)
    }
}
