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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Today", Icons.Default.Home),
    Tab("Plan", Icons.Default.DateRange),
    Tab("Goals", Icons.Default.Star),
    Tab("Log", Icons.Default.List),
    Tab("Settings", Icons.Default.Settings),
)

@Composable
private fun Root(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.onboarded) {
                NavigationBar {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.onboarded) {
                OnboardingScreen(vm)
            } else {
                when (tab) {
                    0 -> TodayScreen(vm)
                    1 -> PlanScreen(vm)
                    2 -> GoalsScreen(vm)
                    3 -> LogScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
            busy?.let { text ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Column(Modifier.padding(start = 12.dp)) { Text(text) }
                    }
                }
            }
        }
    }
}
