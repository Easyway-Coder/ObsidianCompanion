package com.example.obsidiancompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel

// ============================================================================
// Activity & Theme Management
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: NoteViewModel = viewModel()
            val dark = vm.darkMode

            // Keep status/nav bar icon colours in sync with the in-app dark switch
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        0xE6FFFFFF.toInt(),
                        0x801B1B1B.toInt()
                    ) { dark }
                )
                onDispose { }
            }

            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoteApp(vm)
                }
            }
        }
    }
}

// ============================================================================
// App shell: floating taskbar + one screen per tab
// ============================================================================

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    FOCUS_TIMER("Focus Timer", Icons.Outlined.Timer),
    NOTES("Notes", Icons.Outlined.Edit),
    PLAN("Plan", Icons.Outlined.CalendarMonth),
    STATS("Stats", Icons.Outlined.BarChart),
}

@Composable
fun NoteApp(vm: NoteViewModel) {
    var tab by rememberSaveable { mutableStateOf(AppTab.NOTES) }
    val stateHolder = rememberSaveableStateHolder()
    val focusVm: FocusTimerViewModel = viewModel()

    // Kept here (not inside NotesScreen) so they run whichever tab is open
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.flush() }

    Scaffold(
        bottomBar = {
            FloatingTaskbar(
                selected = tab,
                onSelect = { tab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // Gives every tab its own saved state (scroll position, view mode, ...)
            // so nothing resets when you switch tabs.
            stateHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    AppTab.HOME -> HomeScreen()
                    AppTab.FOCUS_TIMER -> FocusTimerScreen(focusVm, vm)
                    AppTab.NOTES -> NotesScreen(
                        vm = vm,
                        darkMode = vm.darkMode,
                        onToggleDarkMode = { vm.setDarkMode(!vm.darkMode) }
                    )
                    AppTab.PLAN -> PlanScreen()
                    AppTab.STATS -> StatsScreen()
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Taskbar: rounded bar where the active tab is slightly elevated and casts a
// shadow shaped exactly like the selected icon + label.
// ----------------------------------------------------------------------------

@Composable
fun FloatingTaskbar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    TaskbarItem(
                        tab = tab,
                        selected = isSelected,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(if (isSelected) 1.3f else 1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskbarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yOffset by animateDpAsState(
        targetValue = if (selected) (-5).dp else 0.dp,
        label = "tabElevationOffset"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val itemColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .offset(y = yOffset),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            // Render an identical copy of the selected item (icon + label) behind it as the shadow
            TaskbarItemContent(
                tab = tab,
                showLabel = true,
                color = Color.Black.copy(alpha = 0.6f),
                contentDescription = null,
                modifier = Modifier
                    .offset(y = 5.5.dp)
                    .blur(radius = 4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            )
        }

        TaskbarItemContent(
            tab = tab,
            showLabel = selected,
            color = itemColor,
            contentDescription = tab.label
        )
    }
}

@Composable
private fun TaskbarItemContent(
    tab: AppTab,
    showLabel: Boolean,
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(26.dp)
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tab.label,
                color = color,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Tab screens. Notes is in NotesScreen.kt, Focus Timer in FocusTimerScreen.kt;
// the rest are intentionally blank for now.
// Build each one inside its own function; the shell above already shows it.
// ----------------------------------------------------------------------------

@Composable
fun HomeScreen() {
    // TODO: Home page (dashboard)
    Box(Modifier.fillMaxSize())
}

@Composable
fun PlanScreen() {
    // TODO: Next-day planner page
    Box(Modifier.fillMaxSize())
}

@Composable
fun StatsScreen() {
    // TODO: Stats page
    Box(Modifier.fillMaxSize())
}