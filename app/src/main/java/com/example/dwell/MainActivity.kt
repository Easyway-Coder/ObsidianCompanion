package com.example.dwell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.luminance
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
            val accent = vm.accent

            // Keep status/nav bar icon colours in sync with the in-app dark switch
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { dark }
                )
                onDispose { }
            }

            val baseScheme = if (dark) darkColorScheme() else lightColorScheme()
            val colorScheme = remember(dark, accent) {
                if (accent != null) {
                    val primary = Color(accent)
                    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
                    baseScheme.copy(
                        primary = primary,
                        onPrimary = onPrimary,
                        primaryContainer = primary.copy(alpha = if (dark) 0.25f else 0.15f),
                        onPrimaryContainer = if (dark) Color.White else Color.Black
                    )
                } else {
                    baseScheme
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
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
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()
    val focusVm: FocusTimerViewModel = viewModel()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.onFolderPicked(uri) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.flush() }

    if (showPreferences) {
        PreferencesScreen(
            vm = vm,
            onBack = { showPreferences = false }
        )
        return
    }

    // Back closes the sidebar first
    BackHandler(enabled = sidebarExpanded) { sidebarExpanded = false }

    Scaffold(
        bottomBar = {
            FloatingTaskbar(selected = tab, onSelect = { tab = it })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // ---- Current tab (always full width) ----
            stateHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    AppTab.HOME -> HomeScreen()
                    AppTab.FOCUS_TIMER -> FocusTimerScreen(focusVm, vm)
                    AppTab.NOTES -> NotesScreen(
                        vm = vm,
                        sidebarExpanded = sidebarExpanded,
                        onToggleSidebar = { sidebarExpanded = !sidebarExpanded }
                    )
                    AppTab.PLAN -> PlanScreen()
                    AppTab.STATS -> StatsScreen()
                }
            }

            // Notes has the toggle in its top bar; every other tab gets this one
            if (tab != AppTab.NOTES) {
                IconButton(
                    onClick = { sidebarExpanded = !sidebarExpanded },
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(SidebarIcon, contentDescription = "Open sidebar")
                }
            }

            // ---- Overlay: dimmed scrim, tap to close ----
            AnimatedVisibility(
                visible = sidebarExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { sidebarExpanded = false }
                )
            }

            // ---- Overlay: the sidebar itself, sliding in from the left ----
            AnimatedVisibility(
                visible = sidebarExpanded,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it }
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    NotesSidebar(
                        folderName = vm.folderName,
                        hasFolder = vm.folderUri != null,
                        notes = vm.notes,
                        selectedNoteUri = vm.selectedNoteUri,
                        onSelectNote = { note ->
                            vm.onNoteSelected(note)
                            sidebarExpanded = false
                        },
                        onPickFolder = { folderPicker.launch(vm.folderUri) },
                        onOpenPreferences = {
                            sidebarExpanded = false
                            showPreferences = true
                        }
                    )
                    VerticalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                TaskbarItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
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
                    .offset(y = 5.dp)
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
fun StatsScreen() {
    // TODO: Stats page
    Box(Modifier.fillMaxSize())
}