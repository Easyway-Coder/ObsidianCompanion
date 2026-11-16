package com.example.dwell.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dwell.components.ChevronsUpDownIcon
import com.example.dwell.navigation.AppTab
import com.example.dwell.screens.FocusTimerViewModel
import com.example.dwell.screens.NoteFile
import com.example.dwell.screens.NoteViewModel

@Composable
fun DwellSidebar(
    currentTab: AppTab,
    vm: NoteViewModel,
    focusVm: FocusTimerViewModel? = null,
    onSelectNote: (NoteFile) -> Unit,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(248.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    AppTab.HOME -> HomeSidebar(
                        folderName = vm.folderName,
                        noteCount = vm.notes.count { !it.isDirectory }
                    )
                    AppTab.NOTES -> NotesSidebarContent(
                        folderName = vm.folderName,
                        hasFolder = vm.folderUri != null,
                        notes = vm.notes,
                        selectedNoteUri = vm.selectedNoteUri,
                        onSelectNote = onSelectNote,
                        onPickFolder = onPickFolder,
                        onOpenSettings = onOpenSettings,
                        pinnedPaths = vm.pinnedPaths.toSet(),
                        onTogglePin = vm::togglePin,
                        onNoteRenamed = vm::onNoteRenamed,
                        onNoteDeleted = vm::onNoteDeleted
                    )
                    AppTab.FOCUS_TIMER -> FocusSidebar(
                        focusVm = focusVm,
                        noteVm = vm
                    )
                    AppTab.PLAN -> PlanSidebar(
                        noteVm = vm
                    )
                    AppTab.STATS -> StatsSidebar(
                        noteVm = vm
                    )
                }
            }

            // Bottom folder switcher row in the structure of Image 2 (no divider/line separator)
            VaultFolderSwitcherRow(
                folderName = vm.folderName,
                onClick = onPickFolder
            )
        }
    }
}

@Composable
internal fun VaultFolderSwitcherRow(
    folderName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ChevronsUpDownIcon,
            contentDescription = "Switch folder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folderName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
