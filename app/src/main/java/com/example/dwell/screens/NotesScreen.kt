package com.example.dwell.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dwell.components.LatexDisplay
import com.example.dwell.components.MarkdownBlock
import com.example.dwell.components.MarkdownText
import com.example.dwell.components.ParsedBlock
import com.example.dwell.components.SidebarIcon
import com.example.dwell.components.TASK_LINE_RE
import com.example.dwell.components.TableAlignment
import com.example.dwell.components.calloutAccentColor
import com.example.dwell.components.calloutIcon
import com.example.dwell.components.formatWikilinkDisplay
import com.example.dwell.components.parseMarkdownBlocksWithRanges
import com.example.dwell.components.rememberMarkdownHighlighter
import com.example.dwell.components.stripInlineCommentsAndBlockId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

// ============================================================================
// ViewModel: folder, multi-tab state & file I/O
// ============================================================================

enum class ViewMode { PREVIEW, EDIT }

data class NoteFile(
    val uri: Uri,
    val name: String,
    val relativePath: String = name,
    val isDirectory: Boolean = false
)

class NoteViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = app.contentResolver
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var folderUri: Uri? by mutableStateOf(prefs.getString("folder_uri", null)?.toUri())
        private set
    var folderName: String by mutableStateOf(folderUri?.let { treeNameOf(it) } ?: "No folder selected")
        private set
    var notes: List<NoteFile> by mutableStateOf(emptyList())
        private set
    var selectedNoteUri: Uri? by mutableStateOf(prefs.getString("note_uri", null)?.toUri())
        private set

    // Open note tabs (persisted in SharedPreferences so open tabs survive app restarts)
    var openTabUris: List<Uri> by mutableStateOf(loadSavedTabUris())
        private set

    private val ioLock = Mutex()           // one file operation at a time
    private var saveJob: Job? = null
    private var dirty = false              // true = in-memory text not yet written to disk
    private var loaded = selectedNoteUri == null && folderUri == null // never save before first read

    // Compose state (not StateFlow) so the text field updates synchronously while typing
    var text by mutableStateOf(if (selectedNoteUri == null && folderUri == null) SAMPLE_MARKDOWN else "")
        private set
    var status by mutableStateOf(
        selectedNoteUri?.let { nameOf(it) }
            ?: folderUri?.let { treeNameOf(it) }
            ?: "No folder selected (editing a sample)"
    )
        private set

    @set:JvmName("setDarkModeInternal")
    var darkMode by mutableStateOf(prefs.getBoolean("dark_mode", false))
        private set

    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean("dark_mode", enabled) }
        darkMode = enabled
    }

    var accent: Int? by mutableStateOf(
        if (prefs.contains("accent_color")) prefs.getInt("accent_color", 0) else null
    )
        private set

    fun updateAccent(colorArgb: Int?) {
        prefs.edit {
            if (colorArgb != null) putInt("accent_color", colorArgb) else remove("accent_color")
        }
        accent = colorArgb
    }

    private fun loadSavedTabUris(): List<Uri> {
        val raw = prefs.getString("open_tab_uris", null)
        val parsed = raw
            ?.split("\n")
            ?.mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() }?.toUri() }
            ?.distinct()
            .orEmpty()
        if (parsed.isNotEmpty()) return parsed
        val active = prefs.getString("note_uri", null)?.toUri()
        return listOfNotNull(active)
    }

    private fun persistOpenTabs(uris: List<Uri>) {
        prefs.edit {
            if (uris.isEmpty()) {
                remove("open_tab_uris")
            } else {
                putString("open_tab_uris", uris.joinToString("\n") { it.toString() })
            }
        }
    }

    // ----- picking / loading folders, notes & tabs -----

    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            saveJob?.cancel()
            saveNow() // finish writing pending edits to the previous note
            try {
                resolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                status = "Couldn't keep access to that folder: ${e.message}"
                return@launch
            }
            prefs.edit {
                putString("folder_uri", treeUri.toString())
                remove("note_uri")
                remove("open_tab_uris")
            }
            folderUri = treeUri
            folderName = queryFolderDisplayName(treeUri)
            openTabUris = emptyList()
            selectedNoteUri = null
            loaded = false
            dirty = false
            refresh()
        }
    }

    fun onNoteSelected(note: NoteFile) {
        if (note.isDirectory) return
        if (notes.none { it.uri == note.uri }) {
            notes = (notes + note).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })
        }
        onNotePicked(note.uri)
    }

    fun onTabSelected(uri: Uri) {
        onNotePicked(uri)
    }

    fun closeTab(uri: Uri) {
        viewModelScope.launch {
            val wasSelected = uri == selectedNoteUri
            if (wasSelected) {
                saveJob?.cancel()
                saveNow()
            }
            val currentIdx = openTabUris.indexOf(uri)
            val remaining = openTabUris.filterNot { it == uri }
            openTabUris = remaining
            persistOpenTabs(remaining)

            if (wasSelected) {
                val nextUri = if (remaining.isNotEmpty()) {
                    val targetIdx = currentIdx.coerceIn(0, remaining.lastIndex)
                    remaining[targetIdx]
                } else {
                    null
                }
                selectedNoteUri = nextUri
                loaded = false
                dirty = false
                prefs.edit {
                    if (nextUri != null) {
                        putString("note_uri", nextUri.toString())
                    } else {
                        remove("note_uri")
                    }
                }
                if (nextUri != null) {
                    loadSelectedNote()
                } else {
                    text = ""
                    status = if (folderUri != null) "$folderName (no open tabs)" else "No open tabs"
                }
            }
        }
    }

    fun createNewNoteInTab(rawInputName: String) {
        val currentFolder = folderUri ?: return
        val cleanPath = rawInputName.trim().trim('/')
        if (cleanPath.isEmpty()) return

        viewModelScope.launch {
            saveJob?.cancel()
            saveNow()
            val created = withContext(Dispatchers.IO) {
                runCatching {
                    val parts = cleanPath.split('/').map { it.trim() }.filter { it.isNotEmpty() }
                    if (parts.isEmpty()) return@runCatching null
                    val rawName = parts.last()
                    val fileName = if (rawName.contains('.')) rawName else "$rawName.md"
                    val folderSegments = parts.dropLast(1)

                    val rootDocId = DocumentsContract.getTreeDocumentId(currentFolder)
                    var parentDocUri = DocumentsContract.buildDocumentUriUsingTree(currentFolder, rootDocId)
                    var prefixPath = ""
                    for (seg in folderSegments) {
                        prefixPath = if (prefixPath.isEmpty()) seg else "$prefixPath/$seg"
                        val existingDir = notes.firstOrNull {
                            it.isDirectory && it.relativePath.equals(prefixPath, ignoreCase = true)
                        }
                        parentDocUri = existingDir?.uri ?: DocumentsContract.createDocument(
                            resolver,
                            parentDocUri,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            seg
                        ) ?: return@runCatching null
                    }

                    val docUri = DocumentsContract.createDocument(
                        resolver,
                        parentDocUri,
                        "text/markdown",
                        fileName
                    ) ?: return@runCatching null

                    val heading = "# ${fileName.removeSuffix(".md")}"
                    resolver.openOutputStream(docUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(heading)
                    }

                    val relPath = if (folderSegments.isEmpty()) {
                        fileName
                    } else {
                        "${folderSegments.joinToString("/")}/$fileName"
                    }
                    NoteFile(uri = docUri, name = fileName, relativePath = relPath, isDirectory = false)
                }.getOrNull()
            }

            if (created != null) {
                notes = (notes + created)
                    .distinctBy { it.uri }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })
                onNotePicked(created.uri)
            }
        }
    }

    fun tabTitleFor(uri: Uri): String {
        val match = notes.firstOrNull { !it.isDirectory && it.uri == uri }
        val baseName = (match?.name ?: nameOf(uri)).removeSuffix(".md")
        if (match != null) {
            val duplicateBaseInOpenTabs = openTabUris.count { tabUri ->
                val other = notes.firstOrNull { !it.isDirectory && it.uri == tabUri }
                (other?.name ?: nameOf(tabUri)).removeSuffix(".md").equals(baseName, ignoreCase = true)
            } > 1
            if (duplicateBaseInOpenTabs && match.relativePath != match.name) {
                return match.relativePath.removeSuffix(".md")
            }
        }
        return baseName
    }

    // Resolves an Obsidian [[Wikilink]] target to a note in the current vault if one exists
    fun openWikilink(rawTarget: String) {
        val cleanTarget = rawTarget
            .substringBefore('|')
            .substringBefore('#')
            .trim()
            .removeSuffix(".md")
        if (cleanTarget.isEmpty()) return
        val match = notes.firstOrNull { note ->
            !note.isDirectory && (
                    note.relativePath.removeSuffix(".md").equals(cleanTarget, ignoreCase = true) ||
                            note.name.removeSuffix(".md").equals(cleanTarget, ignoreCase = true)
                    )
        }
        if (match != null) {
            onNoteSelected(match)
        }
    }

    fun onNotePicked(uri: Uri) {
        if (uri !in openTabUris) {
            val updatedTabs = openTabUris + uri
            openTabUris = updatedTabs
            persistOpenTabs(updatedTabs)
        }
        if (uri == selectedNoteUri && loaded) return
        viewModelScope.launch {
            saveJob?.cancel()
            saveNow() // finish writing pending edits to the previous note
            if (folderUri == null) {
                try {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Uri may already belong to a persisted tree permission
                }
            }
            prefs.edit { putString("note_uri", uri.toString()) }
            selectedNoteUri = uri
            loaded = false
            dirty = false
            loadSelectedNote()
        }
    }

    // Re-read the folder and active file (called whenever the app comes to the foreground)
    fun refresh() {
        viewModelScope.launch {
            val currentFolder = folderUri
            if (currentFolder != null) {
                try {
                    val fName = queryFolderDisplayName(currentFolder)
                    val scanned = listFilesInFolder(currentFolder)
                    val noteFiles = scanned.filter { !it.isDirectory }
                    folderName = fName
                    notes = scanned

                    val validNoteUris = noteFiles.map { it.uri }.toSet()
                    var updatedTabs = openTabUris.filter { it in validNoteUris }
                    val currentNote = selectedNoteUri

                    if (updatedTabs.isEmpty()) {
                        val fallback = when {
                            currentNote != null && currentNote in validNoteUris -> currentNote
                            noteFiles.isNotEmpty() -> noteFiles.first().uri
                            else -> null
                        }
                        if (fallback != null) {
                            updatedTabs = listOf(fallback)
                        }
                    }

                    if (updatedTabs != openTabUris) {
                        openTabUris = updatedTabs
                        persistOpenTabs(updatedTabs)
                    }

                    val validSelection = when {
                        currentNote != null && currentNote in updatedTabs -> currentNote
                        updatedTabs.isNotEmpty() -> updatedTabs.first()
                        else -> null
                    }

                    if (validSelection != currentNote) {
                        selectedNoteUri = validSelection
                        loaded = false
                        dirty = false
                        prefs.edit {
                            if (validSelection != null) {
                                putString("note_uri", validSelection.toString())
                            } else {
                                remove("note_uri")
                            }
                        }
                    }

                    if (validSelection == null) {
                        text = ""
                        loaded = false
                        status = if (noteFiles.isEmpty()) "$fName (no notes found)" else "$fName (no open tabs)"
                        return@launch
                    }
                } catch (e: Exception) {
                    status = "Couldn't read folder: ${e.message}"
                    return@launch
                }
            }

            loadSelectedNote()
        }
    }

    private suspend fun loadSelectedNote() {
        val uri = selectedNoteUri ?: return
        if (dirty) return // local edits are about to be saved; don't clobber them
        ioLock.withLock {
            try {
                val fromDisk = readText(uri)
                if (dirty) return@withLock // user typed while we were reading
                if (fromDisk != text) text = fromDisk
                loaded = true
                status = displayStatusFor(uri)
            } catch (e: Exception) {
                status = "Couldn't read note: ${e.message}"
            }
        }
    }

    // Called when the app goes to the background
    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch { saveNow() }
    }

    // ----- edits (all of them just produce new text, which is then auto-saved) -----

    fun onTextEdited(newText: String) {
        if (newText == text) return
        text = newText
        scheduleSave()
    }

    // Flip "[ ]" <-> "[x]" on one line of the note
    fun toggleTask(lineIndex: Int) {
        val lines = text.split("\n").toMutableList()
        val line = lines.getOrNull(lineIndex) ?: return
        val m = TASK_LINE_RE.find(line) ?: return
        val currentMark = m.groupValues[2]
        val newMark = if (currentMark == " ") "x" else " "
        lines[lineIndex] = line.replaceRange(m.groups[2]!!.range, newMark)
        onTextEdited(lines.joinToString("\n"))
    }

    // Appends "- [ ] <title>" at the end of the note. Returns false if nothing was added.
    fun addTask(title: String): Boolean {
        val clean = title.trim().replace("\n", " ")
        if (clean.isEmpty()) return false
        if (selectedNoteUri != null && !loaded) {
            status = "Note not loaded yet"
            return false
        }
        val lines = text.split("\n")
        val endsWithNewline = lines.lastOrNull() == ""
        val body = if (endsWithNewline) lines.dropLast(1) else lines
        val updated = buildList {
            addAll(body)
            add("- [ ] $clean")
            if (endsWithNewline) add("")
        }
        onTextEdited(updated.joinToString("\n"))
        return true
    }

    // Replaces the text of one task line, keeping its checkbox state and indent
    fun editTask(lineIndex: Int, newTitle: String): Boolean {
        val clean = newTitle.trim().replace("\n", " ")
        if (clean.isEmpty()) return false
        val lines = text.split("\n").toMutableList()
        val line = lines.getOrNull(lineIndex) ?: return false
        val m = TASK_LINE_RE.find(line) ?: return false
        lines[lineIndex] = line.replaceRange(m.groups[3]!!.range, clean)
        onTextEdited(lines.joinToString("\n"))
        return true
    }

    // Deletes one task line, but only if it is still the task we expect
    fun removeTask(lineIndex: Int, expectedTitle: String) {
        val lines = text.split("\n").toMutableList()
        val m = lines.getOrNull(lineIndex)?.let { TASK_LINE_RE.find(it) }
        if (m == null || m.groupValues[3].trim() != expectedTitle) {
            status = "Note changed, nothing deleted"
            return
        }
        lines.removeAt(lineIndex)
        onTextEdited(lines.joinToString("\n"))
    }

    // ----- saving -----

    private fun scheduleSave() {
        if (selectedNoteUri == null || !loaded) return
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600) // debounce: save shortly after the user stops typing
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val uri = selectedNoteUri ?: return
        ioLock.withLock {
            if (!dirty) return
            val snapshot = text
            dirty = false
            try {
                writeText(uri, snapshot)
                status = displayStatusFor(uri)
            } catch (e: Exception) {
                dirty = true
                status = "Couldn't save: ${e.message}"
            }
        }
    }

    // ----- folder & file helpers -----

    private suspend fun listFilesInFolder(treeUri: Uri): List<NoteFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NoteFile>()
        val visitedDirs = mutableSetOf<String>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

        fun scanDirectory(parentDocId: String, prefix: String) {
            if (!visitedDirs.add(parentDocId)) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol) ?: continue
                        val name = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol) ?: ""
                        if (name.startsWith(".")) continue // skip hidden folders/files like .obsidian
                        val relPath = if (prefix.isEmpty()) name else "$prefix/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            results.add(
                                NoteFile(
                                    uri = dirUri,
                                    name = name,
                                    relativePath = relPath,
                                    isDirectory = true
                                )
                            )
                            scanDirectory(docId, relPath)
                        } else if (isNoteFile(name, mime)) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            results.add(
                                NoteFile(
                                    uri = docUri,
                                    name = name,
                                    relativePath = relPath,
                                    isDirectory = false
                                )
                            )
                        }
                    }
                }
            }
        }

        scanDirectory(rootDocId, "")
        results.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })
    }

    private fun isNoteFile(name: String, mime: String): Boolean {
        val lower = name.lowercase()
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) return true
        if (mime.startsWith("text/")) return true
        return !mime.startsWith("image/") &&
                !mime.startsWith("video/") &&
                !mime.startsWith("audio/") &&
                mime != "application/pdf" &&
                mime != "application/zip"
    }

    private suspend fun queryFolderDisplayName(treeUri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            resolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: treeNameOf(treeUri)
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IOException("Cannot open file for reading")
    }

    // NonCancellable: a write must never be interrupted halfway ("wt" truncates the file first)
    private suspend fun writeText(uri: Uri, content: String) =
        withContext(Dispatchers.IO + NonCancellable) {
            val out = resolver.openOutputStream(uri, "wt")
                ?: throw IOException("Cannot open file for writing")
            out.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        }

    private fun displayStatusFor(uri: Uri): String {
        val match = notes.firstOrNull { !it.isDirectory && it.uri == uri }
        return if (folderUri != null && match != null) {
            "$folderName / ${match.relativePath}"
        } else {
            nameOf(uri)
        }
    }

    private fun treeNameOf(treeUri: Uri): String =
        runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
                .substringAfterLast(':')
                .substringAfterLast('/')
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Folder"

    private fun nameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Note"
}

// ============================================================================
// Notes screen with multi-note tab bar & collapsible file sidebar
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NoteViewModel,
    sidebarExpanded: Boolean,
    onToggleSidebar: () -> Unit
) {
    val text = vm.text
    val openTabs = vm.openTabUris
    val selectedNoteUri = vm.selectedNoteUri
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.PREVIEW) }
    var showNewTabDialog by rememberSaveable { mutableStateOf(false) }

    if (showNewTabDialog) {
        OpenNoteTabDialog(
            notes = vm.notes.filter { !it.isDirectory },
            openTabUris = openTabs,
            hasFolder = vm.folderUri != null,
            onSelectExistingNote = { note ->
                showNewTabDialog = false
                vm.onNoteSelected(note)
            },
            onCreateNewNote = { name ->
                showNewTabDialog = false
                vm.createNewNoteInTab(name)
            },
            onOpenSidebar = {
                showNewTabDialog = false
                if (!sidebarExpanded) onToggleSidebar()
            },
            onDismiss = { showNewTabDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onToggleSidebar) {
                            Icon(
                                imageVector = SidebarIcon,
                                contentDescription = if (sidebarExpanded) "Collapse sidebar" else "Expand sidebar"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "Dwell",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = vm.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        // Shows the mode you'll switch TO: pencil in preview, book in editor
                        IconButton(
                            onClick = {
                                viewMode = if (viewMode == ViewMode.EDIT) ViewMode.PREVIEW else ViewMode.EDIT
                            }
                        ) {
                            if (viewMode == ViewMode.EDIT) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Switch to preview")
                            } else {
                                Icon(Icons.Default.Edit, contentDescription = "Switch to editor")
                            }
                        }
                    }
                )

                if (openTabs.isNotEmpty()) {
                    NoteTabsBar(
                        openTabUris = openTabs,
                        selectedNoteUri = selectedNoteUri,
                        titleForTab = vm::tabTitleFor,
                        onSelectTab = vm::onTabSelected,
                        onCloseTab = vm::closeTab,
                        onAddTab = { showNewTabDialog = true }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        thickness = 1.dp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            if (selectedNoteUri == null && vm.folderUri != null) {
                // All tabs were closed while a vault folder is active
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No note tabs are currently open.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { showNewTabDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("New Tab")
                        }
                        OutlinedButton(onClick = onToggleSidebar) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Open Sidebar")
                        }
                    }
                }
            } else {
                // Keying by selectedNoteUri ensures each note tab gets a clean edit/scroll state
                key(selectedNoteUri) {
                    MarkdownDocumentView(
                        markdown = text,
                        editable = viewMode == ViewMode.EDIT,
                        onTextChange = vm::onTextEdited,
                        onToggleTask = vm::toggleTask,
                        onDeleteTask = vm::removeTask,
                        onWikilinkClick = vm::openWikilink,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteTabsBar(
    openTabUris: List<Uri>,
    selectedNoteUri: Uri?,
    titleForTab: (Uri) -> String,
    onSelectTab: (Uri) -> Unit,
    onCloseTab: (Uri) -> Unit,
    onAddTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Scroll active tab into view when selectedNoteUri changes
    LaunchedEffect(selectedNoteUri, openTabUris.size) {
        val activeIndex = openTabUris.indexOf(selectedNoteUri)
        if (activeIndex >= 0) {
            runCatching { listState.animateScrollToItem(activeIndex) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(openTabUris, key = { it.toString() }) { tabUri ->
                val isSelected = tabUri == selectedNoteUri
                val title = titleForTab(tabUri)
                NoteTabChip(
                    title = title,
                    selected = isSelected,
                    onClick = { onSelectTab(tabUri) },
                    onClose = { onCloseTab(tabUri) }
                )
            }
        }

        // Trailing "+" button to open or create another note in a new tab
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onAddTab),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Open new note tab",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun NoteTabChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = if (selected) {
        accent.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (selected) {
        accent.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .widthIn(min = 96.dp, max = 200.dp)
            .height(34.dp)
            .clip(shape)
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = if (selected) accent else contentColor.copy(alpha = 0.7f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close $title tab",
                tint = contentColor.copy(alpha = 0.75f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun OpenNoteTabDialog(
    notes: List<NoteFile>,
    openTabUris: List<Uri>,
    hasFolder: Boolean,
    onSelectExistingNote: (NoteFile) -> Unit,
    onCreateNewNote: (String) -> Unit,
    onOpenSidebar: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val filteredNotes = remember(notes, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            notes
        } else {
            notes.filter {
                it.name.contains(q, ignoreCase = true) ||
                        it.relativePath.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Open Note in Tab",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search or type new note name") },
                    placeholder = { Text("e.g. Daily Note") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val match = filteredNotes.firstOrNull()
                            if (match != null) {
                                onSelectExistingNote(match)
                            } else if (hasFolder && query.isNotBlank()) {
                                onCreateNewNote(query.trim())
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(Modifier.height(10.dp))

                if (!hasFolder) {
                    Text(
                        text = "Choose a vault folder from the sidebar to open or create additional notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (filteredNotes.isEmpty()) {
                    Text(
                        text = if (query.isNotBlank()) {
                            "No matching note found. Tap \"Create Note\" below to open \"${query.trim()}\" in a new tab."
                        } else {
                            "No notes in this folder yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredNotes, key = { it.uri.toString() }) { note ->
                            val alreadyOpen = note.uri in openTabUris
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectExistingNote(note) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (alreadyOpen) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.name.removeSuffix(".md"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (alreadyOpen) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (note.relativePath != note.name) {
                                        Text(
                                            text = note.relativePath.substringBeforeLast('/'),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (alreadyOpen) {
                                    Text(
                                        text = "Open",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasFolder && query.isNotBlank()) {
                TextButton(onClick = { onCreateNewNote(query.trim()) }) {
                    Text("Create Note", fontWeight = FontWeight.SemiBold)
                }
            } else if (!hasFolder) {
                TextButton(onClick = onOpenSidebar) {
                    Text("Open Sidebar", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ============================================================================
// Preview & Live-Preview Edit composables
// ============================================================================

private class ActiveBlockEditSession(
    val blockIndex: Int, // -1 means appending at the end of the note
    val prefixLines: List<String>,
    val suffixLines: List<String>,
    initialText: String
) {
    var fieldValue by mutableStateOf(
        TextFieldValue(
            text = initialText,
            selection = TextRange(initialText.length)
        )
    )
}

@Composable
fun MarkdownDocumentView(
    markdown: String,
    onToggleTask: (lineIndex: Int) -> Unit,
    onDeleteTask: (lineIndex: Int, title: String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onTextChange: (String) -> Unit = {},
    onWikilinkClick: (String) -> Unit = {}
) {
    var editSession by remember { mutableStateOf<ActiveBlockEditSession?>(null) }

    // Close active inline block editor when switching into locked Preview mode
    LaunchedEffect(editable) {
        if (!editable) editSession = null
    }

    // Back button exits the active block edit first so it renders back as Preview
    BackHandler(enabled = editable && editSession != null) {
        editSession = null
    }

    // Re-parse blocks only when no inline block session is actively mid-keystroke
    val parsedBlocks = remember(markdown, editSession == null) {
        parseMarkdownBlocksWithRanges(markdown)
    }

    var pendingDelete by remember { mutableStateOf<MarkdownBlock.TaskItem?>(null) }

    fun startEditingBlock(index: Int, parsed: ParsedBlock) {
        if (!editable) return
        val allLines = markdown.split("\n")
        val safeStart = parsed.startLine.coerceIn(0, allLines.size)
        val safeEnd = (parsed.endLine + 1).coerceIn(safeStart, allLines.size)
        val prefix = allLines.subList(0, safeStart)
        val suffix = allLines.subList(safeEnd, allLines.size)
        val raw = allLines.subList(safeStart, safeEnd).joinToString("\n")
        editSession = ActiveBlockEditSession(
            blockIndex = index,
            prefixLines = prefix,
            suffixLines = suffix,
            initialText = raw
        )
    }

    fun startAppendingAtEnd() {
        if (!editable) return
        val allLines = if (markdown.isEmpty()) emptyList() else markdown.split("\n")
        editSession = ActiveBlockEditSession(
            blockIndex = -1,
            prefixLines = allLines,
            suffixLines = emptyList(),
            initialText = ""
        )
    }

    fun updateSessionText(session: ActiveBlockEditSession, newValue: TextFieldValue) {
        session.fieldValue = newValue
        val updatedDoc = buildList {
            addAll(session.prefixLines)
            add(newValue.text)
            addAll(session.suffixLines)
        }.joinToString("\n")
        onTextChange(updatedDoc)
    }

    // Confirmation before deleting, since this edits the real note
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete task?") },
            text = { Text("\"${task.text}\" will be removed from your note.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTask(task.lineIndex, task.text)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    val highlighter = rememberMarkdownHighlighter()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(parsedBlocks) { index, parsed ->
            val currentSession = editSession
            if (editable && currentSession != null && currentSession.blockIndex == index) {
                InlineBlockEditor(
                    value = currentSession.fieldValue,
                    onValueChange = { updateSessionText(currentSession, it) },
                    highlighter = highlighter
                )
            } else {
                val block = parsed.block
                if (block is MarkdownBlock.TaskItem) {
                    TaskBlock(
                        task = block,
                        editable = editable,
                        onToggle = {
                            if (editable) {
                                editSession = null
                                onToggleTask(block.lineIndex)
                            }
                        },
                        onRequestDelete = {
                            if (editable) {
                                editSession = null
                                pendingDelete = block
                            }
                        },
                        onEditBlock = { startEditingBlock(index, parsed) },
                        onWikilinkClick = onWikilinkClick
                    )
                } else if (editable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { startEditingBlock(index, parsed) }
                    ) {
                        StaticBlock(block = block, onWikilinkClick = onWikilinkClick)
                    }
                } else {
                    // Locked Preview Mode: read-only selection, no editing
                    SelectionContainer {
                        StaticBlock(block = block, onWikilinkClick = onWikilinkClick)
                    }
                }
            }
        }

        if (editable) {
            item(key = "edit_append_space") {
                val currentSession = editSession
                if (currentSession != null && currentSession.blockIndex == -1) {
                    InlineBlockEditor(
                        value = currentSession.fieldValue,
                        onValueChange = { updateSessionText(currentSession, it) },
                        highlighter = highlighter,
                        placeholder = if (parsedBlocks.isEmpty()) "Write or paste Obsidian Markdown here..." else null
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (parsedBlocks.isEmpty()) 240.dp else 96.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (currentSession != null) {
                                    editSession = null
                                } else {
                                    startAppendingAtEnd()
                                }
                            }
                    ) {
                        if (parsedBlocks.isEmpty()) {
                            Text(
                                text = "Tap here to start writing...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineBlockEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    highlighter: VisualTransformation,
    placeholder: String? = null
) {
    val focusRequester = remember { FocusRequester() }
    val textColor = MaterialTheme.colorScheme.onBackground
    val cursorColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (value.text.isEmpty() && placeholder != null) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 24.sp,
                color = textColor
            ),
            cursorBrush = SolidColor(cursorColor),
            visualTransformation = highlighter
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBlock(
    task: MarkdownBlock.TaskItem,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit,
    editable: Boolean = true,
    onEditBlock: () -> Unit = {},
    onWikilinkClick: (String) -> Unit = {}
) {
    // Items can shift around after edits, so always call the newest callback
    val currentOnDelete by rememberUpdatedState(onRequestDelete)

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.6f },
        confirmValueChange = { value ->
            if (editable && value == SwipeToDismissBoxValue.StartToEnd) {
                currentOnDelete()
                false // snap back; the dialog decides
            } else {
                false
            }
        }
    )

    val isDone = task.status.equals('x', ignoreCase = true)
    val isCancelled = task.status == '-'
    val isInProgress = task.status == '/'
    val displayCleanText = remember(task.text) { stripInlineCommentsAndBlockId(task.text) }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = editable,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            if (editable) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = (task.indentLevel * 16).dp)
                    .then(
                        if (!editable) {
                            Modifier
                        } else {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEditBlock
                            )
                        }
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriStateCheckbox(
                    state = when {
                        isDone -> androidx.compose.ui.state.ToggleableState.On
                        isInProgress || isCancelled -> androidx.compose.ui.state.ToggleableState.Indeterminate
                        else -> androidx.compose.ui.state.ToggleableState.Off
                    },
                    onClick = if (editable) onToggle else null,
                    enabled = editable
                )
                MarkdownText(
                    text = displayCleanText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (isDone || isCancelled) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isDone || isCancelled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    onWikilinkClick = onWikilinkClick
                )
            }
        }
    )
}

@Composable
fun StaticBlock(
    block: MarkdownBlock,
    onWikilinkClick: (String) -> Unit = {}
) {
    when (block) {
        is MarkdownBlock.Frontmatter -> {
            var expanded by rememberSaveable { mutableStateOf(false) }
            val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "propsChevron")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Properties (${block.properties.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (expanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        block.properties.forEach { (k, v) ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(100.dp)
                                )
                                MarkdownText(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    onWikilinkClick = onWikilinkClick
                                )
                            }
                        }
                    }
                }
            }
        }

        is MarkdownBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                4 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                5 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                MarkdownText(
                    text = block.text,
                    style = style,
                    color = MaterialTheme.colorScheme.onBackground,
                    onWikilinkClick = onWikilinkClick
                )
                if (block.level <= 2) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }
            }
        }

        is MarkdownBlock.Paragraph -> MarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onBackground,
            onWikilinkClick = onWikilinkClick
        )

        is MarkdownBlock.CodeBlock -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            ) {
                if (block.language.isNotEmpty()) {
                    Text(
                        text = block.language.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = block.code,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        is MarkdownBlock.LatexBlock -> LatexDisplay(
            parts = block.parts,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )

        is MarkdownBlock.Callout -> {
            val accentColor = calloutAccentColor(block.type)
            val isFoldable = block.foldState != null
            var expanded by rememberSaveable(block.type, block.title, block.foldState) {
                mutableStateOf(block.foldState != '-')
            }
            val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "calloutChevron")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFoldable) Modifier.clickable { expanded = !expanded } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = calloutIcon(block.type),
                        contentDescription = block.type,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    MarkdownText(
                        text = block.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accentColor,
                        modifier = Modifier.weight(1f),
                        onWikilinkClick = onWikilinkClick
                    )
                    if (isFoldable) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = accentColor,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotation)
                        )
                    }
                }
                if (expanded && block.body.isNotEmpty()) {
                    MarkdownText(
                        text = block.body,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        onWikilinkClick = onWikilinkClick
                    )
                }
            }
        }

        is MarkdownBlock.Blockquote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                MarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                    onWikilinkClick = onWikilinkClick
                )
            }
        }

        is MarkdownBlock.TableBlock -> {
            val colCount = block.headers.size.coerceAtLeast(1)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0 until colCount) {
                        val align = block.alignments.getOrElse(c) { TableAlignment.START }
                        val textAlign = when (align) {
                            TableAlignment.CENTER -> TextAlign.Center
                            TableAlignment.END -> TextAlign.End
                            TableAlignment.START -> TextAlign.Start
                        }
                        MarkdownText(
                            text = block.headers.getOrElse(c) { "" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                textAlign = textAlign
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                            onWikilinkClick = onWikilinkClick
                        )
                    }
                }
                // Data rows
                block.rows.forEach { row ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (c in 0 until colCount) {
                            val align = block.alignments.getOrElse(c) { TableAlignment.START }
                            val textAlign = when (align) {
                                TableAlignment.CENTER -> TextAlign.Center
                                TableAlignment.END -> TextAlign.End
                                TableAlignment.START -> TextAlign.Start
                            }
                            MarkdownText(
                                text = row.getOrElse(c) { "" },
                                style = MaterialTheme.typography.bodyMedium.copy(textAlign = textAlign),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                onWikilinkClick = onWikilinkClick
                            )
                        }
                    }
                }
            }
        }

        is MarkdownBlock.EmbedBlock -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable { onWikilinkClick(block.target) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Embedded note or file",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = block.aliasOrSize ?: formatWikilinkDisplay(block.target),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (block.aliasOrSize != null) {
                            Text(
                                text = block.target,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        is MarkdownBlock.BulletItem -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (block.indentLevel * 16).dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp)
            )
            MarkdownText(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                onWikilinkClick = onWikilinkClick
            )
        }

        is MarkdownBlock.OrderedItem -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (block.indentLevel * 16).dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "${block.number}.",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            MarkdownText(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                onWikilinkClick = onWikilinkClick
            )
        }

        is MarkdownBlock.FootnoteDefinition -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "[${block.id}]",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            MarkdownText(
                text = block.text,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                onWikilinkClick = onWikilinkClick
            )
        }

        MarkdownBlock.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        is MarkdownBlock.TaskItem -> Unit // rendered by TaskBlock
    }
}

// ============================================================================
// Sample note (shown until a real folder/note is chosen)
// ============================================================================

private val SAMPLE_MARKDOWN = """
---
tags: obsidian, dwell
status: active
---
# Welcome to Dwell

Pick a folder with the **folder icon** and select any note from the **sidebar** on the left — everything you do here is saved straight back to that file. #obsidian #productivity/notes

> [!note] Note
> Default callout with the pencil icon.

> [!abstract]+ Abstract / Summary
> Supports **Callouts**, ==highlighted text==, internal `[[Wikilinks]]`, `![[Embeds]]`, `#tags`, tables, footnotes[^1], and `%%hidden comments%%`.

> [!tip] Tip
> Flame icon for `tip`, `hint`, and `important`.

> [!question]- Question (tap to expand)
> Foldable callout with the question-mark circle icon.

## Try it

- [x] Tick this box in **Preview**
- [/] Half-finished task (`[/]`)
- [ ] Switch to **Editor** and watch the formatting stay while you type ^task-item

### Inline styles & Wikilinks

**Bold**, *italic*, ***bold italic***, ~~strikethrough~~, ==highlighted==, `inline code`, an [external link](https://obsidian.md), a [[Daily Notes#Goals|Custom Wikilink]], and %%this inline comment is hidden in Preview%% a footnote reference[^1].

![[Project Roadmap|Embedded Note: Project Roadmap]]

### Tables

| Syntax | Meaning | Status |
| :--- | :---: | ---: |
| `==text==` | ==Highlight== | Ready |
| `> [!note]` | Callout block | Ready |
| `[[Note\|Alias]]` | Internal link | Ready |

### LaTeX Math

Inline math like Euler's identity §e^{i\pi} + 1 = 0§, the quadratic roots §x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}§ for §a \neq 0§, or **bold text with §x^2§ inside**.

§§
\int_{-\infty}^{\infty} e^{-x^2}\,dx = \sqrt{\pi}
§§

§§\boxed{E = mc^2}§§§§\sum_{k=1}^{n} k = \frac{n(n+1)}{2}§§

### Code

```kotlin
fun greet(user: String) = "Hello, §user!"
```

> Clean code always looks like it was written by someone who cares.

---

[^1]: Footnotes and `%%comments%%` work just like in Obsidian.
""".trimIndent().replace('§', '$')