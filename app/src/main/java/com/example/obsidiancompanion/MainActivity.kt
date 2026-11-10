package com.example.obsidiancompanion

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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

// One regex for every inline token. Group numbers:
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
// ViewModel: ONE source of truth (the note's text) + file I/O
// ============================================================================

enum class ViewMode { PREVIEW, EDIT, SPLIT }

class NoteViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = app.contentResolver
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var noteUri: Uri? = prefs.getString("note_uri", null)?.toUri()
    private val ioLock = Mutex()           // one file operation at a time
    private var saveJob: Job? = null
    private var dirty = false              // true = in-memory text not yet written to disk
    private var loaded = noteUri == null   // never save before the first successful read

    // Compose state (not StateFlow) so the text field updates synchronously while typing
    var text by mutableStateOf(if (noteUri == null) SAMPLE_MARKDOWN else "")
        private set
    var status by mutableStateOf(noteUri?.let { nameOf(it) } ?: "No note selected (editing a sample)")
        private set
    @set:JvmName("setDarkModeInternal")
    var darkMode by mutableStateOf(prefs.getBoolean("dark_mode", false))
        private set

    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean("dark_mode", enabled) }
        darkMode = enabled
    }

    // ----- picking / loading -----

    fun onNotePicked(uri: Uri) {
        viewModelScope.launch {
            saveJob?.cancel()
            saveNow() // finish writing pending edits to the previous note
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                status = "Couldn't keep access to that file: ${e.message}"
                return@launch
            }
            prefs.edit { putString("note_uri", uri.toString()) }
            noteUri = uri
            loaded = false
            dirty = false
            refresh()
        }
    }

    // Re-read the file (called whenever the app comes to the foreground)
    fun refresh() {
        val uri = noteUri ?: return
        if (dirty) return // local edits are about to be saved; don't clobber them
        viewModelScope.launch {
            ioLock.withLock {
                try {
                    val fromDisk = readText(uri)
                    if (dirty) return@withLock // user typed while we were reading
                    if (fromDisk != text) text = fromDisk
                    loaded = true
                    status = nameOf(uri)
                } catch (e: Exception) {
                    status = "Couldn't read note: ${e.message}"
                }
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
        if (noteUri != null && !loaded) {
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
        if (noteUri == null || !loaded) return
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600) // debounce: save shortly after the user stops typing
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val uri = noteUri ?: return
        ioLock.withLock {
            if (!dirty) return
            val snapshot = text
            dirty = false
            try {
                writeText(uri, snapshot)
                status = nameOf(uri)
            } catch (e: Exception) {
                dirty = true
                status = "Couldn't save: ${e.message}"
            }
        }
    }

    // ----- file helpers -----

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

    private fun nameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/') ?: "Note"
}

// ============================================================================
// Activity
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
// App screen
// ============================================================================

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
                    AppTab.FOCUS_TIMER -> FocusTimerScreen()
                    AppTab.NOTES -> NotesScreen(vm)
                    AppTab.PLAN -> PlanScreen()
                    AppTab.STATS -> StatsScreen()
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Taskbar. Colours come from the theme's inverse pair, so it is a light pill with
// dark icons in dark mode, and a dark pill with light icons in light mode.
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
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha=0.12f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    TaskbarItem(
                        tab = tab,
                        selected = isSelected,
                        onClick = { onSelect(tab) },
                        // the selected tab is a bit wider so its label fits
                        modifier = Modifier.weight(if (isSelected) 1.6f else 1f)
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
    val highlight = if (selected) {
        Modifier.background(LocalContentColor.current.copy(alpha = 0.12f))
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .then(highlight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            modifier = Modifier.size(26.dp)
        )
        if (selected) {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Tab screens. Everything except Notes is intentionally blank for now.
// Build each one inside its own function; the shell above already shows it.
// ----------------------------------------------------------------------------

@Composable
fun HomeScreen() {
    // TODO: Home page (dashboard)
    Box(Modifier.fillMaxSize())
}

@Composable
fun FocusTimerScreen() {
    // TODO: Focus timer page
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

// ============================================================================
// Notes screen (the app as it was before the taskbar was added)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(vm: NoteViewModel) {
    val text = vm.text
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.PREVIEW) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.onNotePicked(it) } }

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
                title = {
                    Column {
                        Text(
                            text = "Obsidian Companion",
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
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Choose note")
                    }
                    IconButton(onClick = { vm.setDarkMode(!vm.darkMode) }) {
                        Icon(
                            imageVector = if (vm.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
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

// ============================================================================
// LIVE EDITOR: styling is applied as a VisualTransformation, so the text the
// user types is untouched (same length, same cursor) but is drawn formatted.
// Markers like ** # - [ ] stay visible, just dimmed.
// ============================================================================

private data class HighlightColors(
    val accent: Color,   // list markers, links, wikilinks
    val muted: Color,    // syntax characters (**, #, `, ...)
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

private fun highlightMarkdown(source: String, c: HighlightColors): AnnotatedString =
    buildAnnotatedString {
        append(source)
        var offset = 0
        var inFence = false
        for (line in source.split("\n")) {
            val end = offset + line.length
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    inFence = !inFence
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.muted), offset, end)
                }

                inFence -> addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, background = c.codeBg),
                    offset, end
                )

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

    for (m in INLINE_RE.findAll(line, from)) {
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

private fun buildInline(
    text: String,
    codeBackground: Color,
    linkColor: Color,
    onLinkClick: (String) -> Unit
): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE_RE.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val g = m.groups
        when {
            // [[note]] or [[note|alias]]
            g[1] != null -> withStyle(SpanStyle(color = linkColor)) {
                append(m.groupValues[1].substringAfter('|'))
            }

            // [label](url)
            g[2] != null -> {
                val url = m.groupValues[3].trim()
                val link = LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                ) { onLinkClick(url) }
                withLink(link) { append(m.groupValues[2]) }
            }

            g[4] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = 13.sp)
            ) { append(" ${m.groupValues[4]} ") }

            g[5] != null || g[6] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(m.groupValues[5].ifEmpty { m.groupValues[6] })
            }

            g[7] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(m.groupValues[7])
            }

            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(m.groupValues[8].ifEmpty { m.groupValues[9] })
            }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

@Composable
fun MarkdownText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, codeBg, linkColor, uriHandler) {
        buildInline(text, codeBg, linkColor) { url -> runCatching { uriHandler.openUri(url) } }
    }
    Text(text = annotated, style = style, color = color, modifier = modifier)
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
// Sample note (shown until a real note is chosen)
// ============================================================================

private val SAMPLE_MARKDOWN = """
# Welcome to Obsidian Companion

Pick a note with the **folder icon** and everything you do here is saved straight back to that file.

## Try it

- [x] Tick this box in **Preview**
- [ ] Tick this one too (swipe right to delete)
- [ ] Switch to **Editor** and watch the formatting stay while you type

### Inline styles

**Bold**, *italic*, ~~strikethrough~~, `inline code`, a [link](https://developer.android.com) and a [[Wiki Link]].

### Code

```kotlin
fun greet(user: String) = "Hello, ${'$'}user!"
```

> Clean code always looks like it was written by someone who cares.

---

1. First step
2. Second step
""".trimIndent()