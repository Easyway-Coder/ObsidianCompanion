package com.example.dwell.sidebar

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dwell.components.GlassFolderIcon
import com.example.dwell.components.GlassPlusIcon
import com.example.dwell.components.GlassSearchIcon
import com.example.dwell.components.GlassSettingsIcon
import com.example.dwell.screens.NoteFile

enum class NotesSidebarAction {
    FILES,
    NEW_NOTE,
    SEARCH,
    SETTINGS
}

private sealed class SidebarTreeEntry {
    abstract val key: String
    abstract val depth: Int

    data class FolderEntry(
        val name: String,
        val path: String,
        override val depth: Int,
        val isExpanded: Boolean,
        val isEmpty: Boolean
    ) : SidebarTreeEntry() {
        override val key: String get() = "folder:$path"
    }

    data class NoteEntry(
        val note: NoteFile,
        override val depth: Int
    ) : SidebarTreeEntry() {
        override val key: String get() = "note:${note.uri}"
    }

    data class EmptyFolderHint(
        val folderPath: String,
        override val depth: Int
    ) : SidebarTreeEntry() {
        override val key: String get() = "empty:$folderPath"
    }
}

private class SidebarFolderNode(
    val name: String,
    val path: String
) {
    val subFolders = linkedMapOf<String, SidebarFolderNode>()
    val files = mutableListOf<NoteFile>()
}

private fun buildSidebarTree(items: List<NoteFile>): SidebarFolderNode {
    val root = SidebarFolderNode(name = "", path = "")
    for (item in items) {
        val parts = item.relativePath.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) continue
        if (item.isDirectory) {
            var current = root
            var currentPath = ""
            for (segment in parts) {
                currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
                current = current.subFolders.getOrPut(segment) {
                    SidebarFolderNode(name = segment, path = currentPath)
                }
            }
        } else {
            var current = root
            var currentPath = ""
            for (i in 0 until parts.size - 1) {
                val segment = parts[i]
                currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
                current = current.subFolders.getOrPut(segment) {
                    SidebarFolderNode(name = segment, path = currentPath)
                }
            }
            current.files.add(item)
        }
    }
    return root
}

private fun flattenSidebarTree(
    root: SidebarFolderNode,
    expandedPaths: Set<String>
): List<SidebarTreeEntry> {
    val out = mutableListOf<SidebarTreeEntry>()

    fun visit(node: SidebarFolderNode, depth: Int) {
        val sortedFolders = node.subFolders.values.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        val sortedFiles = node.files.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )

        for (folder in sortedFolders) {
            val isExpanded = folder.path in expandedPaths
            val isEmpty = folder.subFolders.isEmpty() && folder.files.isEmpty()
            out.add(
                SidebarTreeEntry.FolderEntry(
                    name = folder.name,
                    path = folder.path,
                    depth = depth,
                    isExpanded = isExpanded,
                    isEmpty = isEmpty
                )
            )
            if (isExpanded) {
                if (isEmpty) {
                    out.add(
                        SidebarTreeEntry.EmptyFolderHint(
                            folderPath = folder.path,
                            depth = depth + 1
                        )
                    )
                } else {
                    visit(folder, depth + 1)
                }
            }
        }

        for (file in sortedFiles) {
            out.add(SidebarTreeEntry.NoteEntry(note = file, depth = depth))
        }
    }

    visit(root, 0)
    return out
}

private fun ancestorFolderPaths(relativePath: String): List<String> {
    val parts = relativePath.split('/').filter { it.isNotEmpty() }
    if (parts.size <= 1) return emptyList()
    val ancestors = mutableListOf<String>()
    var current = ""
    for (i in 0 until parts.size - 1) {
        current = if (current.isEmpty()) parts[i] else "$current/${parts[i]}"
        ancestors.add(current)
    }
    return ancestors
}

// ============================================================================
// Top Glassy Pill Bar (matching Image 1 structure & frosted glass aesthetic)
// ============================================================================

@Composable
internal fun NotesGlassyHeaderBar(
    selectedAction: NotesSidebarAction,
    onSelectAction: (NotesSidebarAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassFill = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.11f),
                Color.White.copy(alpha = 0.05f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.78f),
                Color.Black.copy(alpha = 0.05f)
            )
        }
    )
    val glassBorder = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.08f)
            )
        } else {
            listOf(
                Color.Black.copy(alpha = 0.16f),
                Color.Black.copy(alpha = 0.06f)
            )
        }
    )
    val activeCapsuleBg = if (isDark) {
        Color.White.copy(alpha = 0.13f)
    } else {
        Color.Black.copy(alpha = 0.09f)
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .height(50.dp)
            .clip(CircleShape)
            .background(glassFill)
            .border(width = 1.dp, brush = glassBorder, shape = CircleShape)
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassyBarIconItem(
                icon = GlassFolderIcon,
                contentDescription = "Files",
                selected = selectedAction == NotesSidebarAction.FILES,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                activeCapsuleBg = activeCapsuleBg,
                onClick = { onSelectAction(NotesSidebarAction.FILES) },
                modifier = Modifier.weight(1f)
            )
            GlassyBarIconItem(
                icon = GlassPlusIcon,
                contentDescription = "New note",
                selected = selectedAction == NotesSidebarAction.NEW_NOTE,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                activeCapsuleBg = activeCapsuleBg,
                onClick = { onSelectAction(NotesSidebarAction.NEW_NOTE) },
                modifier = Modifier.weight(1f)
            )
            GlassyBarIconItem(
                icon = GlassSearchIcon,
                contentDescription = "Search notes",
                selected = selectedAction == NotesSidebarAction.SEARCH,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                activeCapsuleBg = activeCapsuleBg,
                onClick = { onSelectAction(NotesSidebarAction.SEARCH) },
                modifier = Modifier.weight(1f)
            )
            GlassyBarIconItem(
                icon = GlassSettingsIcon,
                contentDescription = "Settings",
                selected = selectedAction == NotesSidebarAction.SETTINGS,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                activeCapsuleBg = activeCapsuleBg,
                onClick = { onSelectAction(NotesSidebarAction.SETTINGS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GlassyBarIconItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    accentColor: Color,
    inactiveColor: Color,
    activeCapsuleBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(if (selected) activeCapsuleBg else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) accentColor else inactiveColor,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
internal fun NotesSidebarContent(
    folderName: String,
    hasFolder: Boolean,
    notes: List<NoteFile>,
    selectedNoteUri: Uri?,
    onSelectNote: (NoteFile) -> Unit,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeAction by rememberSaveable { mutableStateOf(NotesSidebarAction.FILES) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Store expanded folder paths as a saveable List<String> (Bundle-safe on Android)
    var expandedFolders by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Automatically expand parent folders of the active note when selectedNoteUri changes
    LaunchedEffect(selectedNoteUri, notes) {
        val activeNote = notes.firstOrNull { !it.isDirectory && it.uri == selectedNoteUri }
        if (activeNote != null) {
            val needed = ancestorFolderPaths(activeNote.relativePath)
            if (needed.any { it !in expandedFolders }) {
                expandedFolders = (expandedFolders + needed).distinct()
            }
        }
    }

    val filteredNotes = remember(notes, activeAction, searchQuery) {
        if (activeAction == NotesSidebarAction.SEARCH && searchQuery.isNotBlank()) {
            notes.filter {
                !it.isDirectory && it.name.contains(searchQuery.trim(), ignoreCase = true)
            }
        } else {
            notes
        }
    }

    val expandedSet = remember(expandedFolders) { expandedFolders.toSet() }
    val treeRoot = remember(filteredNotes) { buildSidebarTree(filteredNotes) }
    val visibleEntries = remember(treeRoot, expandedSet) {
        flattenSidebarTree(treeRoot, expandedSet)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Top Glassy Action Bar (replaces the old folder label header) ----
        NotesGlassyHeaderBar(
            selectedAction = activeAction,
            onSelectAction = { action ->
                if (action == NotesSidebarAction.SETTINGS) {
                    onOpenSettings()
                } else {
                    activeAction = if (activeAction == action && action == NotesSidebarAction.SEARCH) {
                        NotesSidebarAction.FILES
                    } else {
                        action
                    }
                }
            }
        )

        // Optional inline search bar when Search icon is selected
        if (activeAction == NotesSidebarAction.SEARCH) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search notes...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // ---- File & folder tree ----
        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasFolder -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select a folder to browse and edit your notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onPickFolder) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Choose folder")
                        }
                    }
                }

                notes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notes found in this folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(visibleEntries, key = { it.key }) { entry ->
                            when (entry) {
                                is SidebarTreeEntry.FolderEntry -> {
                                    val chevronRotation by animateFloatAsState(
                                        targetValue = if (entry.isExpanded) 0f else -90f,
                                        label = "folderChevron"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                expandedFolders = if (entry.isExpanded) {
                                                    expandedFolders - entry.path
                                                } else {
                                                    expandedFolders + entry.path
                                                }
                                            }
                                            .padding(
                                                start = (8 + entry.depth * 14).dp,
                                                end = 10.dp,
                                                top = 8.dp,
                                                bottom = 8.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (entry.isExpanded) "Collapse folder" else "Expand folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .rotate(chevronRotation)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (entry.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                is SidebarTreeEntry.NoteEntry -> {
                                    val note = entry.note
                                    val selected = note.uri == selectedNoteUri
                                    val containerColor = if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                    val contentColor = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(containerColor)
                                            .clickable { onSelectNote(note) }
                                            .padding(
                                                start = (12 + entry.depth * 14).dp,
                                                end = 12.dp,
                                                top = 9.dp,
                                                bottom = 9.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = contentColor.copy(alpha = if (selected) 1f else 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = note.name.removeSuffix(".md"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                is SidebarTreeEntry.EmptyFolderHint -> {
                                    Text(
                                        text = "Empty folder",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(
                                            start = (28 + entry.depth * 14).dp,
                                            top = 4.dp,
                                            bottom = 4.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotesSidebar(
    folderName: String,
    hasFolder: Boolean,
    notes: List<NoteFile>,
    selectedNoteUri: Uri?,
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
            NotesSidebarContent(
                folderName = folderName,
                hasFolder = hasFolder,
                notes = notes,
                selectedNoteUri = selectedNoteUri,
                onSelectNote = onSelectNote,
                onPickFolder = onPickFolder,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
            // Bottom folder switcher row in the structure of Image 2 (no divider/line separator)
            VaultFolderSwitcherRow(
                folderName = folderName,
                onClick = onPickFolder
            )
        }
    }
}
