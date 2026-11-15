package com.example.dwell.sidebar

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import com.example.dwell.components.GlassFolderIcon
import com.example.dwell.components.GlassPlusIcon
import com.example.dwell.components.GlassSearchIcon
import com.example.dwell.components.GlassSettingsIcon
import com.example.dwell.screens.NoteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NotesSidebarAction {
    FILES,
    NEW_NOTE,
    SEARCH,
    SETTINGS
}

// ============================================================================
// Hovering Plus Dropdown Icons (matching the uploaded image)
// ============================================================================

private val NewNoteMenuIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "NewNoteMenu",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Rounded square with open top-right corner
            moveTo(11f, 4f)
            lineTo(6.5f, 4f)
            quadTo(4f, 4f, 4f, 6.5f)
            lineTo(4f, 17.5f)
            quadTo(4f, 20f, 6.5f, 20f)
            lineTo(17.5f, 20f)
            quadTo(20f, 20f, 20f, 17.5f)
            lineTo(20f, 13f)

            // Diagonal pen / pencil body + nib
            moveTo(17.6f, 3.4f)
            quadTo(19f, 2f, 20.4f, 3.4f)
            lineTo(20.6f, 3.6f)
            quadTo(22f, 5f, 20.6f, 6.4f)
            lineTo(12.2f, 14.8f)
            lineTo(8.8f, 15.2f)
            lineTo(9.2f, 11.8f)
            close()
        }
    }.build()
}

private val NewFolderMenuIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "NewFolderMenu",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Outlined folder with top-left tab
            moveTo(3f, 8f)
            lineTo(3f, 6.5f)
            quadTo(3f, 4.5f, 5f, 4.5f)
            lineTo(9.2f, 4.5f)
            quadTo(10.4f, 4.5f, 11.1f, 5.5f)
            lineTo(12.2f, 7f)
            lineTo(19f, 7f)
            quadTo(21f, 7f, 21f, 9f)
            lineTo(21f, 17.5f)
            quadTo(21f, 19.5f, 19f, 19.5f)
            lineTo(5f, 19.5f)
            quadTo(3f, 19.5f, 3f, 17.5f)
            close()

            // Centered plus (+) inside the folder body
            moveTo(12f, 10.6f)
            lineTo(12f, 16.2f)
            moveTo(9.2f, 13.4f)
            lineTo(14.8f, 13.4f)
        }
    }.build()
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
// Storage Access Framework (SAF) Note & Folder Creation Helpers
// ============================================================================

private fun resolveVaultTreeUri(context: Context, notes: List<NoteFile>): Uri? {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    prefs.getString("folder_uri", null)?.let { return it.toUri() }
    val sampleUri = notes.firstOrNull()?.uri ?: return null
    return runCatching {
        val treeDocId = DocumentsContract.getTreeDocumentId(sampleUri)
        DocumentsContract.buildTreeDocumentUri(sampleUri.authority, treeDocId)
    }.getOrNull()
}

private suspend fun createNoteInVault(
    context: Context,
    treeUri: Uri,
    existingItems: List<NoteFile>,
    rawInputPath: String
): NoteFile? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val cleanPath = rawInputPath.trim().trim('/')
        if (cleanPath.isEmpty()) return@withContext null

        val parts = cleanPath.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return@withContext null

        val rawName = parts.last()
        val fileName = if (rawName.contains('.')) rawName else "$rawName.md"
        val folderSegments = parts.dropLast(1)

        val parentDocUri = ensureSubfolderDocumentUri(resolver, treeUri, existingItems, folderSegments)
            ?: return@withContext null

        val createdUri = DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            "text/markdown",
            fileName
        ) ?: return@withContext null

        val initialHeading = "# ${fileName.removeSuffix(".md")}\n\n"
        resolver.openOutputStream(createdUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(initialHeading)
        }

        val relPath = if (folderSegments.isEmpty()) {
            fileName
        } else {
            "${folderSegments.joinToString("/")}/$fileName"
        }

        NoteFile(
            uri = createdUri,
            name = fileName,
            relativePath = relPath,
            isDirectory = false
        )
    }.getOrNull()
}

private suspend fun createFolderInVault(
    context: Context,
    treeUri: Uri,
    existingItems: List<NoteFile>,
    rawInputPath: String
): NoteFile? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val cleanPath = rawInputPath.trim().trim('/')
        if (cleanPath.isEmpty()) return@withContext null

        val parts = cleanPath.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return@withContext null

        val folderName = parts.last()
        val parentSegments = parts.dropLast(1)

        val parentDocUri = ensureSubfolderDocumentUri(resolver, treeUri, existingItems, parentSegments)
            ?: return@withContext null

        val createdDirUri = DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            folderName
        ) ?: return@withContext null

        val relPath = parts.joinToString("/")
        NoteFile(
            uri = createdDirUri,
            name = folderName,
            relativePath = relPath,
            isDirectory = true
        )
    }.getOrNull()
}

private fun ensureSubfolderDocumentUri(
    resolver: android.content.ContentResolver,
    treeUri: Uri,
    existingItems: List<NoteFile>,
    segments: List<String>
): Uri? {
    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
    var currentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
    var currentPath = ""

    for (seg in segments) {
        currentPath = if (currentPath.isEmpty()) seg else "$currentPath/$seg"
        val existingDir = existingItems.firstOrNull {
            it.isDirectory && it.relativePath.equals(currentPath, ignoreCase = true)
        }
        currentDocUri = if (existingDir != null) {
            existingDir.uri
        } else {
            DocumentsContract.createDocument(
                resolver,
                currentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                seg
            ) ?: return null
        }
    }
    return currentDocUri
}

// ============================================================================
// Top Glassy Pill Bar + Hovering Plus Dropdown (matching Image 1 & Plus menu)
// ============================================================================

@Composable
internal fun NotesGlassyHeaderBar(
    selectedAction: NotesSidebarAction,
    showPlusMenu: Boolean,
    onSelectAction: (NotesSidebarAction) -> Unit,
    onDismissPlusMenu: () -> Unit,
    onNewNoteClick: () -> Unit,
    onNewFolderClick: () -> Unit,
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
    val density = LocalDensity.current

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
                selected = selectedAction == NotesSidebarAction.FILES && !showPlusMenu,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                activeCapsuleBg = activeCapsuleBg,
                onClick = { onSelectAction(NotesSidebarAction.FILES) },
                modifier = Modifier.weight(1f)
            )

            // Plus (+) icon with anchored hovering drop-down menu
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                GlassyBarIconItem(
                    icon = GlassPlusIcon,
                    contentDescription = "Create new note or folder",
                    selected = showPlusMenu || selectedAction == NotesSidebarAction.NEW_NOTE,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor,
                    activeCapsuleBg = activeCapsuleBg,
                    onClick = { onSelectAction(NotesSidebarAction.NEW_NOTE) },
                    modifier = Modifier.fillMaxSize()
                )

                if (showPlusMenu) {
                    val popupOffset = with(density) {
                        IntOffset(x = 12.dp.roundToPx(), y = 48.dp.roundToPx())
                    }
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = popupOffset,
                        onDismissRequest = onDismissPlusMenu,
                        properties = PopupProperties(focusable = true)
                    ) {
                        PlusHoverDropdownMenu(
                            onNewNote = onNewNoteClick,
                            onNewFolder = onNewFolderClick
                        )
                    }
                }
            }

            GlassyBarIconItem(
                icon = GlassSearchIcon,
                contentDescription = "Search notes",
                selected = selectedAction == NotesSidebarAction.SEARCH && !showPlusMenu,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                activeCapsuleBg = activeCapsuleBg,
                onClick = { onSelectAction(NotesSidebarAction.SEARCH) },
                modifier = Modifier.weight(1f)
            )
            GlassyBarIconItem(
                icon = GlassSettingsIcon,
                contentDescription = "Settings",
                selected = selectedAction == NotesSidebarAction.SETTINGS && !showPlusMenu,
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
private fun PlusHoverDropdownMenu(
    onNewNote: () -> Unit,
    onNewFolder: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val menuBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f)
    }
    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.10f)
            )
        } else {
            listOf(
                Color.Black.copy(alpha = 0.16f),
                Color.Black.copy(alpha = 0.08f)
            )
        }
    )
    val shape = RoundedCornerShape(14.dp)

    Surface(
        shape = shape,
        color = menuBg,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .width(184.dp)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PlusDropdownMenuRow(
                icon = NewNoteMenuIcon,
                label = "New Note",
                onClick = onNewNote
            )
            PlusDropdownMenuRow(
                icon = NewFolderMenuIcon,
                label = "New Folder",
                onClick = onNewFolder
            )
        }
    }
}

@Composable
private fun PlusDropdownMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
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
private fun NameInputDialog(
    title: String,
    label: String,
    placeholder: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (value.isNotBlank()) onConfirm(value.trim())
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) }
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
    onRefreshNotes: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeAction by rememberSaveable { mutableStateOf(NotesSidebarAction.FILES) }
    var showPlusMenu by rememberSaveable { mutableStateOf(false) }
    var showNewNoteDialog by rememberSaveable { mutableStateOf(false) }
    var showNewFolderDialog by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Keep locally created notes/folders visible immediately before the next full folder refresh
    val locallyCreatedItems = remember(folderName) { mutableStateListOf<NoteFile>() }

    val allNotes = remember(notes, locallyCreatedItems.size) {
        val existingPaths = notes.map { it.relativePath.lowercase() }.toSet()
        val extra = locallyCreatedItems.filter { it.relativePath.lowercase() !in existingPaths }
        (notes + extra).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })
    }

    // Store expanded folder paths as a saveable List<String> (Bundle-safe on Android)
    var expandedFolders by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Automatically expand parent folders of the active note when selectedNoteUri changes
    LaunchedEffect(selectedNoteUri, allNotes) {
        val activeNote = allNotes.firstOrNull { !it.isDirectory && it.uri == selectedNoteUri }
        if (activeNote != null) {
            val needed = ancestorFolderPaths(activeNote.relativePath)
            if (needed.any { it !in expandedFolders }) {
                expandedFolders = (expandedFolders + needed).distinct()
            }
        }
    }

    if (showNewNoteDialog) {
        NameInputDialog(
            title = "New Note",
            label = "Note name",
            placeholder = "e.g. Daily Note or Folder/Note",
            confirmText = "Create",
            onDismiss = { showNewNoteDialog = false },
            onConfirm = { inputName ->
                showNewNoteDialog = false
                val treeUri = resolveVaultTreeUri(context, allNotes)
                if (treeUri == null) {
                    onPickFolder()
                } else {
                    scope.launch {
                        val created = createNoteInVault(context, treeUri, allNotes, inputName)
                        if (created != null) {
                            locallyCreatedItems.add(created)
                            val parents = ancestorFolderPaths(created.relativePath)
                            if (parents.isNotEmpty()) {
                                expandedFolders = (expandedFolders + parents).distinct()
                            }
                            activeAction = NotesSidebarAction.FILES
                            onSelectNote(created)
                            onRefreshNotes()
                        }
                    }
                }
            }
        )
    }

    if (showNewFolderDialog) {
        NameInputDialog(
            title = "New Folder",
            label = "Folder name",
            placeholder = "e.g. Projects or Work/Archive",
            confirmText = "Create",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { inputFolder ->
                showNewFolderDialog = false
                val treeUri = resolveVaultTreeUri(context, allNotes)
                if (treeUri == null) {
                    onPickFolder()
                } else {
                    scope.launch {
                        val createdDir = createFolderInVault(context, treeUri, allNotes, inputFolder)
                        if (createdDir != null) {
                            locallyCreatedItems.add(createdDir)
                            val toExpand = ancestorFolderPaths(createdDir.relativePath) + createdDir.relativePath
                            expandedFolders = (expandedFolders + toExpand).distinct()
                            activeAction = NotesSidebarAction.FILES
                            onRefreshNotes()
                        }
                    }
                }
            }
        )
    }

    val filteredNotes = remember(allNotes, activeAction, searchQuery) {
        if (activeAction == NotesSidebarAction.SEARCH && searchQuery.isNotBlank()) {
            allNotes.filter {
                !it.isDirectory && it.name.contains(searchQuery.trim(), ignoreCase = true)
            }
        } else {
            allNotes
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
            showPlusMenu = showPlusMenu,
            onSelectAction = { action ->
                when (action) {
                    NotesSidebarAction.SETTINGS -> {
                        showPlusMenu = false
                        onOpenSettings()
                    }
                    NotesSidebarAction.NEW_NOTE -> {
                        showPlusMenu = !showPlusMenu
                    }
                    NotesSidebarAction.SEARCH -> {
                        showPlusMenu = false
                        activeAction = if (activeAction == NotesSidebarAction.SEARCH) {
                            NotesSidebarAction.FILES
                        } else {
                            NotesSidebarAction.SEARCH
                        }
                    }
                    NotesSidebarAction.FILES -> {
                        showPlusMenu = false
                        activeAction = NotesSidebarAction.FILES
                    }
                }
            },
            onDismissPlusMenu = { showPlusMenu = false },
            onNewNoteClick = {
                showPlusMenu = false
                if (!hasFolder) {
                    onPickFolder()
                } else {
                    showNewNoteDialog = true
                }
            },
            onNewFolderClick = {
                showPlusMenu = false
                if (!hasFolder) {
                    onPickFolder()
                } else {
                    showNewFolderDialog = true
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

                allNotes.isEmpty() -> {
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
    onRefreshNotes: () -> Unit = {},
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
                onRefreshNotes = onRefreshNotes,
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