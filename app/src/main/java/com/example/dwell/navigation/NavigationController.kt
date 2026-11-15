package com.example.dwell.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dwell.components.FloatingTaskbar
import com.example.dwell.components.SidebarIcon
import com.example.dwell.screens.FocusTimerScreen
import com.example.dwell.screens.FocusTimerViewModel
import com.example.dwell.screens.HomeScreen
import com.example.dwell.screens.NoteViewModel
import com.example.dwell.screens.NotesScreen
import com.example.dwell.screens.PlanScreen
import com.example.dwell.screens.PreferencesScreen
import com.example.dwell.screens.SettingsScreen
import com.example.dwell.screens.StatsScreen
import com.example.dwell.sidebar.DwellSidebar

@Composable
fun NavigationController(vm: NoteViewModel) {
    var tab by rememberSaveable { mutableStateOf(AppTab.NOTES) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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

    if (showSettings) {
        SettingsScreen(
            onOpenPreferences = { showPreferences = true },
            onBack = { showSettings = false }
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
                    DwellSidebar(
                        currentTab = tab,
                        vm = vm,
                        focusVm = focusVm,
                        onSelectNote = { note ->
                            vm.onNoteSelected(note)
                            sidebarExpanded = false
                        },
                        onPickFolder = { folderPicker.launch(vm.folderUri) },
                        onOpenSettings = {
                            sidebarExpanded = false
                            showSettings = true
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

@Composable
fun NoteApp(vm: NoteViewModel) {
    NavigationController(vm = vm)
}
