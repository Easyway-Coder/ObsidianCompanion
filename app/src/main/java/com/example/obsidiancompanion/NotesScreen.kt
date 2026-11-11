package com.example.obsidiancompanion

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
// Shared regexes
// ============================================================================

// "- [ ] Buy milk" / "  * [x] Done".  g1 = indent, g2 = mark, g3 = text
private val TASK_LINE_RE = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s+(.*)""")

// Same thing but only the prefix (used by the live-editor highlighter)
private val TASK_PREFIX_RE = Regex("""^(\s*)([-*+])\s+(\[[ xX]])\s+""")
private val HEADING_RE = Regex("""^(\s*)(#{1,6})\s+""")
private val QUOTE_RE = Regex("""^(\s*)>\s?""")
private val BULLET_RE = Regex("""^(\s*)[-*+]\s+""")
private val ORDERED_RE = Regex("""^(\s*)\d+\.\s+""")
private val RULE_RE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
private val FRONTMATTER_KEY_RE = Regex("""^[\w-]+:.*""")

// One regex for the non-math inline tokens. Math ($..$ / $$..$$) is found separately
// (findMathSpans) and masked out first, so it can sit inside bold / italic / links.
// Group numbers:
// 1 wikilink | 2,3 link label,url | 4 code | 5 **bold** | 6 __bold__ | 7 ~~strike~~ | 8 *italic* | 9 _italic_
private val INLINE_RE = Regex(
    listOf(
        """\[\[([^\]\n]+)]]""",
        """\[([^\]\n]+)]\(([^)\n]+)\)""",
        """`([^`\n]+)`""",
        """\*\*([^*\n]+)\*\*""",
        """(?<!\w)__([^_\n]+)__(?!\w)""",
        """~~([^~\n]+)~~""",
        """\*([^*\n]+)\*""",
        """(?<!\w)_([^_\n]+)_(?!\w)"""
    ).joinToString("|")
)

// ============================================================================
// Math detection (shared by the editor highlighter and the preview)
// ============================================================================

private class MathSpan(val range: IntRange, val latex: String, val display: Boolean) {
    val delim: Int get() = if (display) 2 else 1
}

private const val MASK_CHAR = '\uE000'

// Finds $inline$ and $$display$$ math in a piece of text. Skips `code` and \$ escapes.
// Inline rules (same as Obsidian): no space right after the opening $ or right before
// the closing $, and the closing $ isn't followed by a digit ("costs $5 and $10" is not math).
private fun findMathSpans(s: String): List<MathSpan> {
    val out = mutableListOf<MathSpan>()
    var i = 0
    while (i < s.length) {
        when (s[i]) {
            '\\' -> i += 2
            '`' -> {
                val end = s.indexOf('`', i + 1)
                i = if (end == -1) i + 1 else end + 1
            }
            '$' -> {
                val display = s.startsWith("$$", i)
                val d = if (display) 2 else 1
                if (!display && (i + 1 >= s.length || s[i + 1].isWhitespace())) { i++; continue }
                var j = i + d
                var close = -1
                while (j < s.length) {
                    val ch = s[j]
                    if (ch == '\\') { j += 2; continue }
                    if (ch == '$') {
                        if (display) {
                            if (s.startsWith("$$", j)) { close = j; break }
                        } else if (!s[j - 1].isWhitespace() && !(j + 1 < s.length && s[j + 1].isDigit())) {
                            close = j; break
                        }
                    }
                    j++
                }
                if (close == -1) { i += d; continue }
                if (close > i + d) out.add(MathSpan(i..close + d - 1, s.substring(i + d, close).trim(), display))
                i = close + d
            }
            else -> i++
        }
    }
    return out
}

// Same-length copy of s with every math span blanked out, so the emphasis regexes
// can never see (or be confused by) the * _ ~ characters inside math.
private fun maskSpans(s: String, spans: List<MathSpan>): String {
    if (spans.isEmpty()) return s
    val chars = s.toCharArray()
    for (sp in spans) for (k in sp.range) chars[k] = MASK_CHAR
    return String(chars)
}

// "$$a$$$$b$$" -> ["a", "b"]; null when the text isn't made purely of $$...$$ groups.
// The quadruple dollar ($$$$) is the break between two equations.
private fun parseDisplayMathParts(text: String): List<String>? {
    val s = text.trim()
    val parts = mutableListOf<String>()
    var pos = 0
    while (pos < s.length) {
        if (!s.startsWith("$$", pos)) return null
        val close = s.indexOf("$$", pos + 2)
        if (close == -1) return null
        parts.add(s.substring(pos + 2, close).trim())
        pos = close + 2
        while (pos < s.length && s[pos].isWhitespace()) pos++
    }
    val nonEmpty = parts.filter { it.isNotEmpty() }
    return nonEmpty.ifEmpty { null }
}

// ============================================================================
// ViewModel: folder & note state + file I/O
// ============================================================================

enum class ViewMode { PREVIEW, EDIT, SPLIT }

data class NoteFile(
    val uri: Uri,
    val name: String,
    val relativePath: String = name
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

    init {
        if (folderUri != null || selectedNoteUri != null) {
            refresh()
        }
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean("dark_mode", enabled) }
        darkMode = enabled
    }

    // ----- picking / loading folders & notes -----

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
            }
            folderUri = treeUri
            folderName = queryFolderDisplayName(treeUri)
            selectedNoteUri = null
            loaded = false
            dirty = false
            refresh()
        }
    }

    fun onNoteSelected(note: NoteFile) {
        onNotePicked(note.uri)
    }

    fun onNotePicked(uri: Uri) {
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
                    folderName = fName
                    notes = scanned

                    val currentNote = selectedNoteUri
                    val validSelection = when {
                        currentNote != null && scanned.any { it.uri == currentNote } -> currentNote
                        scanned.isNotEmpty() -> scanned.first().uri
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
                        status = "$fName (no notes found)"
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
        val newMark = if (m.groupValues[2] == " ") "x" else " "
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
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

        fun scanDirectory(parentDocId: String, prefix: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
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
                        scanDirectory(docId, relPath)
                    } else if (isNoteFile(name, mime)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        results.add(NoteFile(uri = docUri, name = name, relativePath = relPath))
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
        val match = notes.firstOrNull { it.uri == uri }
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
// Notes screen with collapsible file sidebar
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NoteViewModel,
    darkMode: Boolean = vm.darkMode,
    onToggleDarkMode: () -> Unit = { vm.setDarkMode(!vm.darkMode) }
) {
    val text = vm.text
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.PREVIEW) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(true) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            vm.onFolderPicked(uri)
            sidebarExpanded = true
        }
    }

    if (showAddDialog) {
        var title by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New task") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Task") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = { if (vm.addTask(title)) showAddDialog = false }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { sidebarExpanded = !sidebarExpanded }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { folderPicker.launch(vm.folderUri) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Choose folder")
                    }
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            imageVector = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle dark mode"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewMode != ViewMode.EDIT) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            AnimatedVisibility(
                visible = sidebarExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally()
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    NotesSidebar(
                        folderName = vm.folderName,
                        hasFolder = vm.folderUri != null,
                        notes = vm.notes,
                        selectedNoteUri = vm.selectedNoteUri,
                        onSelectNote = { note -> vm.onNoteSelected(note) },
                        onPickFolder = { folderPicker.launch(vm.folderUri) }
                    )
                    VerticalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ViewMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = viewMode == mode,
                            onClick = { viewMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size)
                        ) {
                            Text(
                                when (mode) {
                                    ViewMode.PREVIEW -> "Preview"
                                    ViewMode.EDIT -> "Editor"
                                    ViewMode.SPLIT -> "Split"
                                }
                            )
                        }
                    }
                }

                when (viewMode) {
                    ViewMode.PREVIEW -> MarkdownDocumentView(
                        markdown = text,
                        onToggleTask = vm::toggleTask,
                        onDeleteTask = vm::removeTask,
                        modifier = Modifier.fillMaxSize()
                    )

                    ViewMode.EDIT -> LiveMarkdownEditor(
                        text = text,
                        onTextChange = vm::onTextEdited,
                        modifier = Modifier.fillMaxSize()
                    )

                    ViewMode.SPLIT -> Column(Modifier.fillMaxSize()) {
                        LiveMarkdownEditor(
                            text = text,
                            onTextChange = vm::onTextEdited,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        HorizontalDivider(thickness = 2.dp)
                        MarkdownDocumentView(
                            markdown = text,
                            onToggleTask = vm::toggleTask,
                            onDeleteTask = vm::removeTask,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesSidebar(
    folderName: String,
    hasFolder: Boolean,
    notes: List<NoteFile>,
    selectedNoteUri: Uri?,
    onSelectNote: (NoteFile) -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onPickFolder,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Change folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

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
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(notes, key = { it.uri.toString() }) { note ->
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
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = contentColor.copy(alpha = if (selected) 1f else 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.name.removeSuffix(".md"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (note.relativePath != note.name) {
                                        Text(
                                            text = note.relativePath.substringBeforeLast('/'),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = contentColor.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
}

// ============================================================================
// LIVE EDITOR: styling is applied as a VisualTransformation, so the text the
// user types is untouched (same length, same cursor) but is drawn formatted.
// Markers like ** # - [ ] $ $$ stay visible, just dimmed.
// ============================================================================

private data class HighlightColors(
    val accent: Color,   // list markers, links, wikilinks, math
    val muted: Color,    // syntax characters (**, #, `, $, ...)
    val soft: Color,     // quote / completed-task text
    val codeBg: Color
)

private class MarkdownHighlighter(private val colors: HighlightColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightMarkdown(text.text, colors), OffsetMapping.Identity)
}

@Composable
fun LiveMarkdownEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val highlighter = remember(scheme) {
        MarkdownHighlighter(
            HighlightColors(
                accent = scheme.primary,
                muted = scheme.onSurface.copy(alpha = 0.40f),
                soft = scheme.onSurfaceVariant,
                codeBg = scheme.surfaceVariant
            )
        )
    }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.padding(16.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
        visualTransformation = highlighter,
        placeholder = { Text("Write or paste Markdown here...") },
        shape = RoundedCornerShape(12.dp)
    )
}

private fun headingSize(level: Int): TextUnit = when (level) {
    1 -> 26.sp
    2 -> 23.sp
    3 -> 20.sp
    4 -> 18.sp
    5 -> 17.sp
    else -> 16.sp
}

private fun AnnotatedString.Builder.styleDisplayLine(
    line: String, offset: Int, mono: SpanStyle, math: SpanStyle
) {
    var pos = line.indexOf("$$")
    while (pos != -1) {
        val close = line.indexOf("$$", pos + 2)
        if (close == -1) break
        addStyle(mono, offset + pos, offset + pos + 2)
        addStyle(math, offset + pos + 2, offset + close)
        addStyle(mono, offset + close, offset + close + 2)
        pos = line.indexOf("$$", close + 2)
    }
}

private fun highlightMarkdown(source: String, c: HighlightColors): AnnotatedString =
    buildAnnotatedString {
        append(source)
        val mono = SpanStyle(fontFamily = FontFamily.Monospace, color = c.muted)
        val math = SpanStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = c.accent,
            background = c.codeBg
        )
        var offset = 0
        var inCodeFence = false
        var inMathFence = false
        for (line in source.split("\n")) {
            val end = offset + line.length
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    inCodeFence = !inCodeFence
                    addStyle(mono, offset, end)
                }

                inCodeFence -> addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, background = c.codeBg),
                    offset, end
                )

                // Whole line is $$ ... $$ (possibly several: $$a$$$$b$$)
                !inMathFence && trimmed.startsWith("$$") && parseDisplayMathParts(trimmed) != null ->
                    styleDisplayLine(line, offset, mono, math)

                // Opening of a multi-line display block
                !inMathFence && trimmed.startsWith("$$") && trimmed.indexOf("$$", 2) == -1 -> {
                    inMathFence = true
                    val at = offset + line.indexOf("$$")
                    addStyle(mono, at, at + 2)
                    addStyle(math, at + 2, end)
                }

                inMathFence -> {
                    val close = line.indexOf("$$")
                    if (close == -1) {
                        addStyle(math, offset, end)
                    } else {
                        addStyle(math, offset, offset + close)
                        addStyle(mono, offset + close, offset + close + 2)
                        val rest = line.substring(close + 2)
                        val restTrim = rest.trimStart()
                        if (restTrim.startsWith("$$")) {
                            // "$$$$": one equation ends and the next one starts right away
                            val at = offset + close + 2 + (rest.length - restTrim.length)
                            addStyle(mono, at, at + 2)
                            addStyle(math, at + 2, end)
                        } else {
                            inMathFence = false
                        }
                    }
                }

                trimmed.isNotEmpty() -> styleLine(line, offset, c)
            }
            offset = end + 1 // +1 for the "\n"
        }
    }

private fun AnnotatedString.Builder.styleLine(line: String, start: Int, c: HighlightColors) {
    val lineEnd = start + line.length

    if (RULE_RE.matches(line.trim())) {
        addStyle(SpanStyle(color = c.muted), start, lineEnd)
        return
    }

    HEADING_RE.find(line)?.let { m ->
        val level = m.groupValues[2].length
        val prefixEnd = start + m.range.last + 1
        addStyle(SpanStyle(fontSize = headingSize(level), fontWeight = FontWeight.Bold), start, lineEnd)
        addStyle(SpanStyle(color = c.muted), start + m.groups[2]!!.range.first, prefixEnd)
        styleInline(line, m.range.last + 1, start, c)
        return
    }

    QUOTE_RE.find(line)?.let { m ->
        val prefixEnd = start + m.range.last + 1
        addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), start, prefixEnd)
        addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = c.soft), prefixEnd, lineEnd)
        styleInline(line, m.range.last + 1, start, c)
        return
    }

    TASK_PREFIX_RE.find(line)?.let { m ->
        val checked = m.groupValues[3][1] != ' '
        val markerStart = start + m.groups[2]!!.range.first
        val markerEnd = start + m.groups[3]!!.range.last + 1
        val prefixEnd = start + m.range.last + 1
        addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), markerStart, markerEnd)
        if (checked) {
            addStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough, color = c.soft),
                prefixEnd, lineEnd
            )
        }
        styleInline(line, m.range.last + 1, start, c)
        return
    }

    (BULLET_RE.find(line) ?: ORDERED_RE.find(line))?.let { m ->
        val markerStart = start + m.groups[1]!!.range.last + 1
        val prefixEnd = start + m.range.last + 1
        addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), markerStart, prefixEnd)
        styleInline(line, m.range.last + 1, start, c)
        return
    }

    styleInline(line, 0, start, c)
}

private fun AnnotatedString.Builder.styleInline(
    line: String,
    from: Int,
    start: Int,
    c: HighlightColors
) {
    if (from >= line.length) return
    val dim = SpanStyle(color = c.muted)
    val spans = findMathSpans(line).filter { it.range.first >= from }
    val masked = maskSpans(line, spans)

    for (m in INLINE_RE.findAll(masked, from)) {
        val a = start + m.range.first
        val b = start + m.range.last + 1
        val g = m.groups

        when {
            // [[wikilink]]
            g[1] != null -> {
                addStyle(dim, a, a + 2)
                addStyle(SpanStyle(color = c.accent), a + 2, b - 2)
                addStyle(dim, b - 2, b)
            }

            // [label](url)
            g[2] != null -> {
                val la = start + g[2]!!.range.first
                val lb = start + g[2]!!.range.last + 1
                addStyle(dim, a, la)
                addStyle(SpanStyle(color = c.accent, textDecoration = TextDecoration.Underline), la, lb)
                addStyle(dim, lb, b)
            }

            // `code`
            g[4] != null -> {
                addStyle(dim, a, a + 1)
                addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBg, fontSize = 14.sp),
                    a + 1, b - 1
                )
                addStyle(dim, b - 1, b)
            }

            // **bold** / __bold__
            g[5] != null || g[6] != null ->
                styleWrapped(a, b, 2, SpanStyle(fontWeight = FontWeight.Bold), dim)

            // ~~strike~~
            g[7] != null ->
                styleWrapped(a, b, 2, SpanStyle(textDecoration = TextDecoration.LineThrough), dim)

            // *italic* / _italic_
            else ->
                styleWrapped(a, b, 1, SpanStyle(fontStyle = FontStyle.Italic), dim)
        }
    }

    // Math last, so it keeps its own look even when it sits inside bold / italic
    val mathStyle = SpanStyle(
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        color = c.accent,
        background = c.codeBg
    )
    for (sp in spans) {
        val a = start + sp.range.first
        val b = start + sp.range.last + 1
        val d = sp.delim
        addStyle(mathStyle, a + d, b - d)
        addStyle(dim, a, a + d)
        addStyle(dim, b - d, b)
    }
}

// Dim the `len` marker characters on both ends, style what's between them
private fun AnnotatedString.Builder.styleWrapped(
    a: Int,
    b: Int,
    len: Int,
    content: SpanStyle,
    dim: SpanStyle
) {
    addStyle(dim, a, a + len)
    addStyle(content, a + len, b - len)
    addStyle(dim, b - len, b)
}

// ============================================================================
// Markdown block parser (for Preview)
// ============================================================================

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class LatexBlock(val parts: List<String>) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String, val indentLevel: Int = 0) : MarkdownBlock()
    data class OrderedItem(val number: Int, val text: String, val indentLevel: Int = 0) : MarkdownBlock()

    // lineIndex = which line of the note this checkbox lives on (so it can be ticked / deleted)
    data class TaskItem(
        val lineIndex: Int,
        val checked: Boolean,
        val text: String,
        val indentLevel: Int = 0
    ) : MarkdownBlock()

    data object Divider : MarkdownBlock()
}

// tab = 2 spaces; every 2 columns = one nesting level
private fun indentLevel(whitespace: String): Int =
    whitespace.fold(0) { acc, ch -> acc + if (ch == '\t') 2 else 1 } / 2

private fun startsNewBlock(trimmed: String): Boolean =
    trimmed.isEmpty() ||
            trimmed.startsWith("```") ||
            trimmed.startsWith("$$") ||
            trimmed.startsWith(">") ||
            HEADING_RE.containsMatchIn(trimmed) ||
            RULE_RE.matches(trimmed) ||
            BULLET_RE.containsMatchIn(trimmed) ||
            ORDERED_RE.containsMatchIn(trimmed)

fun parseMarkdownBlocks(rawMarkdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    // split on "\n" only, so list indexes line up exactly with the file's lines
    val lines = rawMarkdown.split("\n").map { it.trimEnd('\r') }
    var i = 0

    // Skip Obsidian YAML frontmatter (--- key: value ... ---)
    if (lines.firstOrNull()?.trim() == "---" &&
        lines.getOrNull(1)?.matches(FRONTMATTER_KEY_RE) == true
    ) {
        val close = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
        if (close != null) i = close + 1
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Blank
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, code.toString()))
            i++ // closing fence
            continue
        }

        // Display math: "$$ ... $$" on one line or spread over several. Several equations
        // can be chained with a quadruple dollar ("$$a$$$$b$$"), each is shown on its own line.
        if (trimmed.startsWith("$$")) {
            var j = i
            var acc = trimmed
            var parts = parseDisplayMathParts(acc)
            while (parts == null && j + 1 < lines.size && lines[j + 1].isNotBlank()) {
                j++
                acc += "\n" + lines[j].trim()
                parts = parseDisplayMathParts(acc)
            }
            if (parts != null) {
                blocks.add(MarkdownBlock.LatexBlock(parts))
                i = j + 1
                continue
            }
            // not pure display math (e.g. "$$x$$ followed by text"): treat as a paragraph below
        }

        // Horizontal rule
        if (RULE_RE.matches(trimmed)) {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Heading
        val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
        if (headingMatch != null) {
            blocks.add(MarkdownBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2]))
            i++
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val quote = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quote.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quote.joinToString("\n")))
            continue
        }

        // Task item
        val taskMatch = TASK_LINE_RE.find(line)
        if (taskMatch != null) {
            blocks.add(
                MarkdownBlock.TaskItem(
                    lineIndex = i,
                    checked = taskMatch.groupValues[2].isNotBlank(),
                    text = taskMatch.groupValues[3].trim(),
                    indentLevel = indentLevel(taskMatch.groupValues[1])
                )
            )
            i++
            continue
        }

        // Bullet
        val bulletMatch = Regex("""^(\s*)[-*+]\s+(.*)$""").find(line)
        if (bulletMatch != null) {
            blocks.add(
                MarkdownBlock.BulletItem(bulletMatch.groupValues[2], indentLevel(bulletMatch.groupValues[1]))
            )
            i++
            continue
        }

        // Ordered
        val orderedMatch = Regex("""^(\s*)(\d+)\.\s+(.*)$""").find(line)
        if (orderedMatch != null) {
            blocks.add(
                MarkdownBlock.OrderedItem(
                    number = orderedMatch.groupValues[2].toIntOrNull() ?: 1,
                    text = orderedMatch.groupValues[3],
                    indentLevel = indentLevel(orderedMatch.groupValues[1])
                )
            )
            i++
            continue
        }

        // Paragraph: this line is always consumed, then keep going until a new block starts
        val para = mutableListOf(trimmed)
        i++
        while (i < lines.size && !startsNewBlock(lines[i].trim())) {
            para.add(lines[i].trim())
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(para.joinToString(" ")))
    }

    return blocks
}

// ============================================================================
// Inline markdown for Preview
// ============================================================================

private class InlineEnv(
    val orig: String,
    val masked: String,
    val spans: List<MathSpan>,
    val codeBackground: Color,
    val linkColor: Color,
    val onLinkClick: (String) -> Unit,
    val makeMath: (latex: String, display: Boolean) -> InlineTextContent
) {
    val inline = mutableMapOf<String, InlineTextContent>()
    var lastDisplayEnd = -1
}

private class InlineResult(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

private fun buildInline(
    text: String,
    codeBackground: Color,
    linkColor: Color,
    onLinkClick: (String) -> Unit,
    makeMath: (latex: String, display: Boolean) -> InlineTextContent
): InlineResult {
    val spans = findMathSpans(text)
    val env = InlineEnv(text, maskSpans(text, spans), spans, codeBackground, linkColor, onLinkClick, makeMath)
    val annotated = buildAnnotatedString { appendRange(env, 0, text.length) }
    return InlineResult(annotated, env.inline)
}

// Handles the emphasis / link / code tokens inside [from, to) and recurses into their
// contents, so math (and other formatting) nested in bold, italic, links... still renders.
private fun AnnotatedString.Builder.appendRange(env: InlineEnv, from: Int, to: Int) {
    var last = from
    for (m in INLINE_RE.findAll(env.masked.substring(from, to))) {
        val a = from + m.range.first
        val b = from + m.range.last + 1
        if (a > last) appendLeaf(env, last, a)
        val g = m.groups
        when {
            // [[note]] or [[note|alias]]
            g[1] != null -> withStyle(SpanStyle(color = env.linkColor)) {
                val r = g[1]!!.range
                append(env.orig.substring(from + r.first, from + r.last + 1).substringAfter('|'))
            }

            // [label](url)
            g[2] != null -> {
                val label = g[2]!!.range
                val u = g[3]!!.range
                val url = env.orig.substring(from + u.first, from + u.last + 1).trim()
                val link = LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(
                        SpanStyle(
                            color = env.linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                ) { env.onLinkClick(url) }
                withLink(link) { appendRange(env, from + label.first, from + label.last + 1) }
            }

            // `inline code`
            g[4] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = env.codeBackground, fontSize = 13.sp)
            ) {
                val r = g[4]!!.range
                append(" ${env.orig.substring(from + r.first, from + r.last + 1)} ")
            }

            // **bold** / __bold__
            g[5] != null || g[6] != null -> {
                val r = (g[5] ?: g[6])!!.range
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendRange(env, from + r.first, from + r.last + 1)
                }
            }

            // ~~strike~~
            g[7] != null -> {
                val r = g[7]!!.range
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendRange(env, from + r.first, from + r.last + 1)
                }
            }

            // *italic* / _italic_
            else -> {
                val r = (g[8] ?: g[9])!!.range
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendRange(env, from + r.first, from + r.last + 1)
                }
            }
        }
        last = b
    }
    if (last < to) appendLeaf(env, last, to)
}

// Plain text plus math spans (no other formatting tokens left in [a, b))
private fun AnnotatedString.Builder.appendLeaf(env: InlineEnv, a: Int, b: Int) {
    var pos = a
    for (sp in env.spans) {
        if (sp.range.first < pos || sp.range.last >= b) continue
        if (sp.range.first > pos) append(env.orig.substring(pos, sp.range.first))
        // two display equations back to back ($$a$$$$b$$) are separated by a line break
        if (sp.display && env.lastDisplayEnd in 0..sp.range.first &&
            env.orig.substring(env.lastDisplayEnd, sp.range.first).isBlank()
        ) append("\n")
        val id = "math${env.inline.size}"
        env.inline[id] = env.makeMath(sp.latex, sp.display)
        appendInlineContent(id, sp.latex)
        env.lastDisplayEnd = if (sp.display) sp.range.last + 1 else -1
        pos = sp.range.last + 1
    }
    if (pos < b) append(env.orig.substring(pos, b))
}

@Composable
fun MarkdownText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val fonts = rememberMathFonts()
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = if (color != Color.Unspecified) color else LocalContentColor.current
    val fontSizePx = with(density) {
        (if (style.fontSize != TextUnit.Unspecified) style.fontSize else 16.sp).toPx()
    }

    val built = remember(text, codeBg, linkColor, uriHandler, fonts, fontSizePx, textColor, density) {
        val argb = textColor.toArgb()
        buildInline(text, codeBg, linkColor, { url -> runCatching { uriHandler.openUri(url) } }) { latex, display ->
            val lay = layoutLatex(latex, MathCtx(fonts, argb, fontSizePx, display))
            with(density) {
                InlineTextContent(
                    Placeholder(
                        width = lay.w.toDp().toSp(),
                        height = lay.asc.coerceAtLeast(fontSizePx * 0.7f).toDp().toSp(),
                        placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                    )
                ) { MathInline(lay) }
            }
        }
    }
    Text(
        text = built.text,
        inlineContent = built.inline,
        style = style,
        color = color,
        modifier = modifier
    )
}

// ============================================================================
// Preview composables
// ============================================================================

@Composable
fun MarkdownDocumentView(
    markdown: String,
    onToggleTask: (lineIndex: Int) -> Unit,
    onDeleteTask: (lineIndex: Int, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    var pendingDelete by remember { mutableStateOf<MarkdownBlock.TaskItem?>(null) }

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

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(blocks) { block ->
            if (block is MarkdownBlock.TaskItem) {
                TaskBlock(
                    task = block,
                    onToggle = { onToggleTask(block.lineIndex) },
                    onRequestDelete = { pendingDelete = block }
                )
            } else {
                // Selection per block, so it doesn't fight with tapping/swiping checkboxes
                SelectionContainer { StaticBlock(block) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBlock(
    task: MarkdownBlock.TaskItem,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit
) {
    // Items can shift around after edits, so always call the newest callback
    val currentOnDelete by rememberUpdatedState(onRequestDelete)

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.6f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                currentOnDelete()
                false // snap back; the dialog decides
            } else {
                true
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
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
        },
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = (task.indentLevel * 16).dp)
                    .toggleable(
                        value = task.checked,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() }
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = task.checked, onCheckedChange = null)
                MarkdownText(
                    text = task.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (task.checked) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
            }
        }
    )
}

@Composable
fun StaticBlock(block: MarkdownBlock) {
    when (block) {
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
                MarkdownText(block.text, style, MaterialTheme.colorScheme.onBackground)
                if (block.level <= 2) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }
            }
        }

        is MarkdownBlock.Paragraph -> MarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onBackground
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
                    modifier = Modifier.padding(12.dp)
                )
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
                modifier = Modifier.weight(1f)
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
                modifier = Modifier.weight(1f)
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
# Welcome to Obsidian Companion

Pick a folder with the **folder icon** and select any note from the **sidebar** on the left — everything you do here is saved straight back to that file.

## Try it

- [x] Tick this box in **Preview**
- [ ] Tick this one too (swipe right to delete)
- [ ] Switch to **Editor** and watch the formatting stay while you type

### Inline styles

**Bold**, *italic*, ~~strikethrough~~, `inline code`, a [link](https://developer.android.com) and a [[Wiki Link]].

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

1. First step
2. Second step
""".trimIndent().replace('§', '$')