package com.example.dwell

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
// Custom Icons
// ============================================================================

private fun buildStrokeIcon(name: String, strokeWidth: Float = 2.1f, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )
    }.build()

private fun PathBuilder.drawCirclePath(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}

internal val SidebarIcon: ImageVector by lazy {
    buildStrokeIcon("Sidebar", strokeWidth = 2.2f) {
        // Outer rounded square from (3, 3) to (21, 21) with corner radius 3.5
        moveTo(6.5f, 3f)
        lineTo(17.5f, 3f)
        quadTo(21f, 3f, 21f, 6.5f)
        lineTo(21f, 17.5f)
        quadTo(21f, 21f, 17.5f, 21f)
        lineTo(6.5f, 21f)
        quadTo(3f, 21f, 3f, 17.5f)
        lineTo(3f, 6.5f)
        quadTo(3f, 3f, 6.5f, 3f)
        close()
        // Vertical sidebar divider line on the left side
        moveTo(9f, 3f)
        lineTo(9f, 21f)
    }
}

internal val PreferencesIcon: ImageVector by lazy {
    buildStrokeIcon("Preferences", strokeWidth = 2.2f) {
        val r = 2.5f
        val sliders = listOf(
            7.2f to 5f,
            16.8f to 12f,
            10.2f to 19f
        )
        for ((cx, cy) in sliders) {
            moveTo(2f, cy)
            lineTo(cx - r, cy)
            moveTo(cx + r, cy)
            lineTo(22f, cy)
            drawCirclePath(cx, cy, r)
        }
    }
}

// ============================================================================
// Obsidian Callout Icons (matching Obsidian's 13 built-in Lucide icons)
// ============================================================================

private object CalloutIcons {
    // [!note] -> lucide-pencil
    val Note: ImageVector by lazy {
        buildStrokeIcon("CalloutNote") {
            moveTo(17f, 3f)
            quadTo(18.8f, 1.2f, 20.6f, 3f)
            lineTo(21f, 3.4f)
            quadTo(22.8f, 5.2f, 21f, 7f)
            lineTo(7.5f, 20.5f)
            lineTo(2f, 22f)
            lineTo(3.5f, 16.5f)
            close()
            moveTo(15f, 5f)
            lineTo(19f, 9f)
        }
    }

    // [!abstract], [!summary], [!tldr] -> lucide-clipboard-list
    val Abstract: ImageVector by lazy {
        buildStrokeIcon("CalloutAbstract") {
            // Top clip
            moveTo(9f, 2f)
            lineTo(15f, 2f)
            quadTo(16f, 2f, 16f, 3f)
            lineTo(16f, 5f)
            quadTo(16f, 6f, 15f, 6f)
            lineTo(9f, 6f)
            quadTo(8f, 6f, 8f, 5f)
            lineTo(8f, 3f)
            quadTo(8f, 2f, 9f, 2f)
            close()
            // Clipboard board
            moveTo(16f, 4f)
            lineTo(18f, 4f)
            quadTo(20f, 4f, 20f, 6f)
            lineTo(20f, 20f)
            quadTo(20f, 22f, 18f, 22f)
            lineTo(6f, 22f)
            quadTo(4f, 22f, 4f, 20f)
            lineTo(4f, 6f)
            quadTo(4f, 4f, 6f, 4f)
            lineTo(8f, 4f)
            // List dots & lines inside
            moveTo(8f, 11f)
            lineTo(8.05f, 11f)
            moveTo(11f, 11f)
            lineTo(16f, 11f)
            moveTo(8f, 16f)
            lineTo(8.05f, 16f)
            moveTo(11f, 16f)
            lineTo(16f, 16f)
        }
    }

    // [!info] -> lucide-info
    val Info: ImageVector by lazy {
        buildStrokeIcon("CalloutInfo") {
            drawCirclePath(12f, 12f, 10f)
            moveTo(12f, 16f)
            lineTo(12f, 12f)
            moveTo(12f, 8f)
            lineTo(12.05f, 8f)
        }
    }

    // [!todo] -> lucide-check-circle-2
    val Todo: ImageVector by lazy {
        buildStrokeIcon("CalloutTodo") {
            drawCirclePath(12f, 12f, 10f)
            moveTo(9f, 12f)
            lineTo(11f, 14f)
            lineTo(15f, 10f)
        }
    }

    // [!tip], [!hint], [!important] -> lucide-flame
    val Tip: ImageVector by lazy {
        buildStrokeIcon("CalloutTip") {
            moveTo(8.5f, 14.5f)
            curveTo(9.5f, 13f, 10f, 11.5f, 10f, 9.5f)
            curveTo(10f, 7f, 11.5f, 4.5f, 13f, 3f)
            curveTo(13.5f, 6.5f, 15.5f, 8f, 17f, 10f)
            curveTo(18.5f, 12f, 19f, 13.5f, 19f, 15f)
            curveTo(19f, 18.8f, 15.8f, 21f, 12f, 21f)
            curveTo(8.2f, 21f, 5f, 18.5f, 5f, 14.5f)
            curveTo(5f, 12.8f, 5.8f, 11.2f, 7f, 10f)
            curveTo(6.8f, 11.8f, 7.5f, 13.5f, 8.5f, 14.5f)
            close()
        }
    }

    // [!success], [!check], [!done] -> lucide-check
    val Success: ImageVector by lazy {
        buildStrokeIcon("CalloutSuccess") {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }
    }

    // [!question], [!help], [!faq] -> lucide-help-circle
    val Question: ImageVector by lazy {
        buildStrokeIcon("CalloutQuestion") {
            drawCirclePath(12f, 12f, 10f)
            moveTo(9.1f, 9f)
            curveTo(9.6f, 7.4f, 11.2f, 6.7f, 12.8f, 7.1f)
            curveTo(14.3f, 7.4f, 15.2f, 8.7f, 15f, 10.2f)
            curveTo(14.8f, 11.5f, 13f, 12.2f, 12f, 13f)
            lineTo(12f, 14f)
            moveTo(12f, 17f)
            lineTo(12.05f, 17f)
        }
    }

    // [!warning], [!caution], [!attention] -> lucide-alert-triangle
    val Warning: ImageVector by lazy {
        buildStrokeIcon("CalloutWarning") {
            moveTo(10.3f, 3.9f)
            lineTo(1.8f, 18f)
            quadTo(1f, 19.5f, 2.6f, 20.5f)
            quadTo(3.1f, 20.8f, 3.6f, 20.8f)
            lineTo(20.4f, 20.8f)
            quadTo(22.2f, 20.8f, 22.2f, 19.2f)
            quadTo(22.2f, 18.6f, 21.8f, 18f)
            lineTo(13.7f, 3.9f)
            quadTo(12f, 1.4f, 10.3f, 3.9f)
            close()
            moveTo(12f, 9f)
            lineTo(12f, 13f)
            moveTo(12f, 17f)
            lineTo(12.05f, 17f)
        }
    }

    // [!failure], [!fail], [!missing] -> lucide-x
    val Failure: ImageVector by lazy {
        buildStrokeIcon("CalloutFailure") {
            moveTo(18f, 6f)
            lineTo(6f, 18f)
            moveTo(6f, 6f)
            lineTo(18f, 18f)
        }
    }

    // [!danger], [!error] -> lucide-zap
    val Danger: ImageVector by lazy {
        buildStrokeIcon("CalloutDanger") {
            moveTo(13f, 2f)
            lineTo(3f, 14f)
            lineTo(12f, 14f)
            lineTo(11f, 22f)
            lineTo(21f, 10f)
            lineTo(12f, 10f)
            close()
        }
    }

    // [!bug] -> lucide-bug
    val Bug: ImageVector by lazy {
        buildStrokeIcon("CalloutBug") {
            // Body oval
            moveTo(8f, 12f)
            quadTo(8f, 8f, 12f, 8f)
            quadTo(16f, 8f, 16f, 12f)
            lineTo(16f, 16f)
            quadTo(16f, 20f, 12f, 20f)
            quadTo(8f, 20f, 8f, 16f)
            close()
            // Center wing divider
            moveTo(12f, 8f)
            lineTo(12f, 20f)
            // Antennae
            moveTo(8f, 4f)
            lineTo(10f, 8f)
            moveTo(16f, 4f)
            lineTo(14f, 8f)
            // 3 pairs of legs
            moveTo(4f, 10f)
            lineTo(8f, 11f)
            moveTo(20f, 10f)
            lineTo(16f, 11f)
            moveTo(3f, 14f)
            lineTo(8f, 14f)
            moveTo(21f, 14f)
            lineTo(16f, 14f)
            moveTo(4f, 18f)
            lineTo(8f, 17f)
            moveTo(20f, 18f)
            lineTo(16f, 17f)
        }
    }

    // [!example] -> lucide-list
    val Example: ImageVector by lazy {
        buildStrokeIcon("CalloutExample") {
            moveTo(4f, 6f)
            lineTo(4.05f, 6f)
            moveTo(8f, 6f)
            lineTo(20f, 6f)
            moveTo(4f, 12f)
            lineTo(4.05f, 12f)
            moveTo(8f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 18f)
            lineTo(4.05f, 18f)
            moveTo(8f, 18f)
            lineTo(20f, 18f)
        }
    }

    // [!quote], [!cite] -> lucide-quote
    val Quote: ImageVector by lazy {
        buildStrokeIcon("CalloutQuote") {
            moveTo(10f, 11f)
            lineTo(6f, 11f)
            quadTo(4f, 11f, 4f, 9f)
            lineTo(4f, 7f)
            quadTo(4f, 5f, 6f, 5f)
            lineTo(8f, 5f)
            quadTo(10f, 5f, 10f, 7f)
            lineTo(10f, 13f)
            quadTo(10f, 17f, 6f, 19f)

            moveTo(20f, 11f)
            lineTo(16f, 11f)
            quadTo(14f, 11f, 14f, 9f)
            lineTo(14f, 7f)
            quadTo(14f, 5f, 16f, 5f)
            lineTo(18f, 5f)
            quadTo(20f, 5f, 20f, 7f)
            lineTo(20f, 13f)
            quadTo(20f, 17f, 16f, 19f)
        }
    }
}

// ============================================================================
// Obsidian Flavored Markdown (OFM) regexes
// ============================================================================

// "- [ ] Buy milk" / "- [x] Done" / "- [/] In progress" / "- [-] Cancelled"
// g1 = indent, g2 = status char, g3 = text
private val TASK_LINE_RE = Regex("""^(\s*)[-*+]\s+\[([ xX/\->!?])]\s+(.*)""")

// Same thing but only the prefix (used by the live-editor highlighter)
private val TASK_PREFIX_RE = Regex("""^(\s*)([-*+])\s+(\[[ xX/\->!?]])\s+""")
private val HEADING_RE = Regex("""^(\s*)(#{1,6})\s+""")
private val CALLOUT_HEADER_RE = Regex("""^(\s*)>\s*\[!([\w-]+)]([+-])?(?:\s+(.*))?$""")
private val QUOTE_RE = Regex("""^(\s*)>\s?""")
private val BULLET_RE = Regex("""^(\s*)[-*+]\s+""")
private val ORDERED_RE = Regex("""^(\s*)\d+[.)]\s+""")
private val RULE_RE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
private val FRONTMATTER_KEY_RE = Regex("""^([\w-]+)\s*:\s*(.*)$""")
private val FOOTNOTE_DEF_RE = Regex("""^\[\^([^\]]+)]:\s+(.*)$""")
private val TABLE_ROW_RE = Regex("""^\|.*\|$""")
private val TABLE_SEP_RE = Regex("""^\|(?:\s*:?-{3,}:?\s*\|)+$""")
private val BLOCK_ID_END_RE = Regex("""(\s+\^[A-Za-z0-9-]+)\s*$""")

// Inline Obsidian Flavored Markdown tokens (math $..$ / $$..$$ is masked out first):
// Group numbers:
// 1: %%comment%%
// 2: ![[embed]]
// 3: [[wikilink]]
// 4, 5: ![alt](url) image
// 6, 7: [label](url) external link
// 8: `inline code`
// 9: ==highlight==
// 10, 11: ***bold+italic*** or ___bold+italic___
// 12, 13: **bold** or __bold__
// 14: ~~strikethrough~~
// 15, 16: *italic* or _italic_
// 17: [^footnote]
// 18: #tag (Obsidian nested tags like #project/dwell)
// 19: ^block-id at end of line
private val INLINE_RE = Regex(
    listOf(
        """%%([^%\n]*?)%%""",
        """!\[\[([^\]\n]+)]]""",
        """\[\[([^\]\n]+)]]""",
        """!\[([^\]\n]*)]\(([^)\n]+)\)""",
        """\[([^\]\n]+)]\(([^)\n]+)\)""",
        """`([^`\n]+)`""",
        """==([^=\n]+)==""",
        """\*\*\*([^*\n]+)\*\*\*""",
        """(?<!\w)___([^_\n]+)___(?!\w)""",
        """\*\*([^*\n]+)\*\*""",
        """(?<!\w)__([^_\n]+)__(?!\w)""",
        """~~([^~\n]+)~~""",
        """\*([^*\n]+)\*""",
        """(?<!\w)_([^_\n]+)_(?!\w)""",
        """\[\^([^\]\n]+)]""",
        """(?<!\S)#([A-Za-z_][A-Za-z0-9_/-]*)""",
        """(?<=\s)\^([A-Za-z0-9-]+)$"""
    ).joinToString("|")
)

private val INLINE_COMMENT_STRIP_RE = Regex("""%%.*?%%""")

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

// Formats Obsidian wikilinks for display:
// "Folder/Note#Heading|Custom Alias" -> "Custom Alias"
// "Folder/Note#Heading" -> "Note › Heading"
// "Folder/Note#^block" -> "Note"
private fun formatWikilinkDisplay(raw: String): String {
    val target = raw.substringBefore('|').trim()
    val alias = raw.substringAfter('|', "").trim()
    if (alias.isNotEmpty()) return alias
    val notePart = target.substringBefore('#').substringAfterLast('/').removeSuffix(".md")
    val anchorPart = target.substringAfter('#', "")
    return when {
        anchorPart.startsWith("^") -> notePart.ifEmpty { anchorPart }
        anchorPart.isNotEmpty() && notePart.isNotEmpty() -> "$notePart › $anchorPart"
        anchorPart.isNotEmpty() -> anchorPart
        else -> notePart.ifEmpty { target }
    }
}

// ============================================================================
// ViewModel: folder & note state + file I/O
// ============================================================================

enum class ViewMode { PREVIEW, EDIT }

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

    // Resolves an Obsidian [[Wikilink]] target to a note in the current vault if one exists
    fun openWikilink(rawTarget: String) {
        val cleanTarget = rawTarget
            .substringBefore('|')
            .substringBefore('#')
            .trim()
            .removeSuffix(".md")
        if (cleanTarget.isEmpty()) return
        val match = notes.firstOrNull { note ->
            note.relativePath.removeSuffix(".md").equals(cleanTarget, ignoreCase = true) ||
                    note.name.removeSuffix(".md").equals(cleanTarget, ignoreCase = true)
        }
        if (match != null) {
            onNoteSelected(match)
        }
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
    sidebarExpanded: Boolean,
    onToggleSidebar: () -> Unit
) {
    val text = vm.text
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.PREVIEW) }

    Scaffold(
        topBar = {
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
                            maxLines = 2,
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            // Preview Mode acts as a read-only lock; Edit Mode renders the exact same
            // Preview blocks while unlocking inline block editing and task actions.
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

@Composable
fun NotesSidebar(
    folderName: String,
    hasFolder: Boolean,
    notes: List<NoteFile>,
    selectedNoteUri: Uri?,
    onSelectNote: (NoteFile) -> Unit,
    onPickFolder: () -> Unit,
    darkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    onOpenPreferences: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Header ----
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
                IconButton(onClick = onPickFolder, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Change folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // ---- File list (takes all the space between header and settings) ----
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

            // ---- Preferences / Settings row (bottom) ----
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            if (onOpenPreferences != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPreferences)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = PreferencesIcon,
                        contentDescription = "Preferences",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Preferences",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = darkMode,
                            role = Role.Switch,
                            onValueChange = onDarkModeChange
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dark Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = darkMode, onCheckedChange = null)
                }
            }
        }
    }
}

// ============================================================================
// LIVE EDITOR: styling is applied as a VisualTransformation, so the text the
// user types is untouched (same length, same cursor) but is drawn formatted.
// Markers like ** # - [ ] == %% $ $$ stay visible, just dimmed.
// ============================================================================

private data class HighlightColors(
    val accent: Color,      // list markers, links, wikilinks, tags, callouts, math
    val muted: Color,       // syntax characters (**, #, `, $, ==, %%, ...)
    val soft: Color,        // quote / completed-task text
    val codeBg: Color,
    val highlightBg: Color  // ==highlight== background
)

private class MarkdownHighlighter(private val colors: HighlightColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightMarkdown(text.text, colors), OffsetMapping.Identity)
}

@Composable
private fun rememberMarkdownHighlighter(): MarkdownHighlighter {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        MarkdownHighlighter(
            HighlightColors(
                accent = scheme.primary,
                muted = scheme.onSurface.copy(alpha = 0.40f),
                soft = scheme.onSurfaceVariant,
                codeBg = scheme.surfaceVariant,
                highlightBg = Color(0xFFFFD60A).copy(alpha = 0.30f)
            )
        )
    }
}

@Composable
fun LiveMarkdownEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlighter = rememberMarkdownHighlighter()
    val textColor = MaterialTheme.colorScheme.onBackground
    val cursorColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.padding(16.dp)) {
        if (text.isEmpty()) {
            Text(
                text = "Write or paste Obsidian Markdown here...",
                style = LocalTextStyle.current.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = textColor
            ),
            cursorBrush = SolidColor(cursorColor),
            visualTransformation = highlighter
        )
    }
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
        val commentStyle = SpanStyle(fontStyle = FontStyle.Italic, color = c.muted)
        val math = SpanStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = c.accent,
            background = c.codeBg
        )
        val lines = source.split("\n")
        var offset = 0
        var inCodeFence = false
        var inMathFence = false
        var inCommentFence = false
        var inFrontmatter = false

        for ((lineIdx, line) in lines.withIndex()) {
            val end = offset + line.length
            val trimmed = line.trim()

            when {
                // YAML Frontmatter at the very top of the note
                lineIdx == 0 && trimmed == "---" && lines.getOrNull(1)?.matches(FRONTMATTER_KEY_RE) == true -> {
                    inFrontmatter = true
                    addStyle(mono, offset, end)
                }

                inFrontmatter -> {
                    if (trimmed == "---" || trimmed == "...") {
                        inFrontmatter = false
                        addStyle(mono, offset, end)
                    } else {
                        val colonIdx = line.indexOf(':')
                        if (colonIdx != -1) {
                            addStyle(
                                SpanStyle(fontFamily = FontFamily.Monospace, color = c.accent, fontWeight = FontWeight.Medium),
                                offset, offset + colonIdx + 1
                            )
                            addStyle(
                                SpanStyle(fontFamily = FontFamily.Monospace, color = c.soft),
                                offset + colonIdx + 1, end
                            )
                        } else {
                            addStyle(mono, offset, end)
                        }
                    }
                }

                // Multi-line Obsidian %% comment block
                !inCodeFence && !inMathFence && trimmed == "%%" -> {
                    inCommentFence = !inCommentFence
                    addStyle(commentStyle, offset, end)
                }

                inCommentFence -> {
                    val close = line.indexOf("%%")
                    if (close == -1) {
                        addStyle(commentStyle, offset, end)
                    } else {
                        addStyle(commentStyle, offset, offset + close + 2)
                        inCommentFence = false
                    }
                }

                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
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
    val trimmed = line.trim()

    if (RULE_RE.matches(trimmed)) {
        addStyle(SpanStyle(color = c.muted), start, lineEnd)
        return
    }

    // Table separator row: | --- | :---: |
    if (TABLE_SEP_RE.matches(trimmed)) {
        addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.muted), start, lineEnd)
        return
    }

    // Table data/header row: | Cell 1 | Cell 2 |
    if (TABLE_ROW_RE.matches(trimmed)) {
        for (i in line.indices) {
            if (line[i] == '|') {
                addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), start + i, start + i + 1)
            }
        }
        styleInline(line, 0, start, c)
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

    // Obsidian Callout header: > [!note]+ Title
    CALLOUT_HEADER_RE.find(line)?.let { m ->
        val typeGroup = m.groups[2]!!
        val calloutColor = calloutAccentColor(typeGroup.value)
        addStyle(SpanStyle(color = calloutColor, fontWeight = FontWeight.Bold), start, lineEnd)
        val titleGroup = m.groups[4]
        if (titleGroup != null) {
            styleInline(line, titleGroup.range.first, start, c)
        }
        return
    }

    QUOTE_RE.find(line)?.let { m ->
        val prefixEnd = start + m.range.last + 1
        addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), start, prefixEnd)
        addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = c.soft), prefixEnd, lineEnd)
        styleInline(line, m.range.last + 1, start, c)
        return
    }

    // Footnote definition: [^1]: Footnote text
    FOOTNOTE_DEF_RE.find(line)?.let { m ->
        val prefixEnd = start + m.groups[1]!!.range.last + 3 // includes "]:"
        addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.SemiBold), start, prefixEnd)
        styleInline(line, m.groups[2]!!.range.first, start, c)
        return
    }

    TASK_PREFIX_RE.find(line)?.let { m ->
        val statusChar = m.groupValues[3][1]
        val struck = statusChar.equals('x', ignoreCase = true) || statusChar == '-'
        val markerStart = start + m.groups[2]!!.range.first
        val markerEnd = start + m.groups[3]!!.range.last + 1
        val prefixEnd = start + m.range.last + 1
        addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold), markerStart, markerEnd)
        if (struck) {
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
            // %%comment%%
            g[1] != null -> {
                addStyle(SpanStyle(color = c.muted, fontStyle = FontStyle.Italic), a, b)
            }

            // ![[embed]]
            g[2] != null -> {
                addStyle(dim, a, a + 3)
                addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium), a + 3, b - 2)
                addStyle(dim, b - 2, b)
            }

            // [[wikilink]]
            g[3] != null -> {
                addStyle(dim, a, a + 2)
                addStyle(SpanStyle(color = c.accent), a + 2, b - 2)
                addStyle(dim, b - 2, b)
            }

            // ![alt](url)
            g[4] != null -> {
                val la = start + g[4]!!.range.first
                val lb = start + g[4]!!.range.last + 1
                addStyle(dim, a, la)
                addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium), la, lb)
                addStyle(dim, lb, b)
            }

            // [label](url)
            g[6] != null -> {
                val la = start + g[6]!!.range.first
                val lb = start + g[6]!!.range.last + 1
                addStyle(dim, a, la)
                addStyle(SpanStyle(color = c.accent, textDecoration = TextDecoration.Underline), la, lb)
                addStyle(dim, lb, b)
            }

            // `code`
            g[8] != null -> {
                addStyle(dim, a, a + 1)
                addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBg, fontSize = 14.sp),
                    a + 1, b - 1
                )
                addStyle(dim, b - 1, b)
            }

            // ==highlight==
            g[9] != null -> {
                styleWrapped(
                    a, b, 2,
                    SpanStyle(background = c.highlightBg, fontWeight = FontWeight.Medium),
                    dim
                )
            }

            // ***bold+italic*** / ___bold+italic___
            g[10] != null || g[11] != null ->
                styleWrapped(
                    a, b, 3,
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    dim
                )

            // **bold** / __bold__
            g[12] != null || g[13] != null ->
                styleWrapped(a, b, 2, SpanStyle(fontWeight = FontWeight.Bold), dim)

            // ~~strike~~
            g[14] != null ->
                styleWrapped(a, b, 2, SpanStyle(textDecoration = TextDecoration.LineThrough), dim)

            // *italic* / _italic_
            g[15] != null || g[16] != null ->
                styleWrapped(a, b, 1, SpanStyle(fontStyle = FontStyle.Italic), dim)

            // [^footnote]
            g[17] != null -> {
                addStyle(dim, a, a + 2)
                addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.SemiBold), a + 2, b - 1)
                addStyle(dim, b - 1, b)
            }

            // #tag
            g[18] != null -> {
                addStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium), a, b)
            }

            // ^block-id
            g[19] != null -> {
                addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.muted, fontSize = 12.sp), a, b)
            }
        }
    }

    // Math last, so it keeps its own look even when it sits inside bold / italic / highlight
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
// Obsidian Flavored Markdown block parser (for Preview & Live Edit)
// ============================================================================

enum class TableAlignment { START, CENTER, END }

sealed class MarkdownBlock {
    data class Frontmatter(val properties: List<Pair<String, String>>) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class LatexBlock(val parts: List<String>) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class Callout(
        val type: String,
        val title: String,
        val body: String,
        val foldState: Char? = null // '+' expanded foldable, '-' collapsed foldable, null static
    ) : MarkdownBlock()
    data class TableBlock(
        val headers: List<String>,
        val alignments: List<TableAlignment>,
        val rows: List<List<String>>
    ) : MarkdownBlock()
    data class EmbedBlock(val target: String, val aliasOrSize: String?) : MarkdownBlock()
    data class BulletItem(val text: String, val indentLevel: Int = 0) : MarkdownBlock()
    data class OrderedItem(val number: Int, val text: String, val indentLevel: Int = 0) : MarkdownBlock()

    // lineIndex = which line of the note this checkbox lives on (so it can be ticked / deleted)
    data class TaskItem(
        val lineIndex: Int,
        val checked: Boolean,
        val status: Char = if (checked) 'x' else ' ',
        val text: String,
        val indentLevel: Int = 0
    ) : MarkdownBlock()

    data class FootnoteDefinition(val id: String, val text: String) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

internal data class ParsedBlock(
    val block: MarkdownBlock,
    val startLine: Int,
    val endLine: Int,
    val rawText: String
)

// tab = 2 spaces; every 2 columns = one nesting level
private fun indentLevel(whitespace: String): Int =
    whitespace.fold(0) { acc, ch -> acc + if (ch == '\t') 2 else 1 } / 2

private fun stripInlineCommentsAndBlockId(s: String): String =
    s.replace(INLINE_COMMENT_STRIP_RE, "").replace(BLOCK_ID_END_RE, "").trimEnd()

private fun parseTableCells(rowLine: String): List<String> =
    rowLine.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }

private fun parseTableAlignments(sepLine: String): List<TableAlignment> =
    parseTableCells(sepLine).map { cell ->
        val c = cell.trim()
        when {
            c.startsWith(":") && c.endsWith(":") -> TableAlignment.CENTER
            c.endsWith(":") -> TableAlignment.END
            else -> TableAlignment.START
        }
    }

private fun startsNewBlock(trimmed: String): Boolean =
    trimmed.isEmpty() ||
            trimmed.startsWith("```") ||
            trimmed.startsWith("~~~") ||
            trimmed.startsWith("%%") ||
            trimmed.startsWith("$$") ||
            trimmed.startsWith(">") ||
            (trimmed.startsWith("![[") && trimmed.endsWith("]]")) ||
            TABLE_ROW_RE.matches(trimmed) ||
            FOOTNOTE_DEF_RE.containsMatchIn(trimmed) ||
            HEADING_RE.containsMatchIn(trimmed) ||
            RULE_RE.matches(trimmed) ||
            BULLET_RE.containsMatchIn(trimmed) ||
            ORDERED_RE.containsMatchIn(trimmed)

fun parseMarkdownBlocks(rawMarkdown: String): List<MarkdownBlock> =
    parseMarkdownBlocksWithRanges(rawMarkdown).map { it.block }

internal fun parseMarkdownBlocksWithRanges(rawMarkdown: String): List<ParsedBlock> {
    val blocks = mutableListOf<ParsedBlock>()
    // split on "\n" only, so list indexes line up exactly with the file's lines
    val lines = rawMarkdown.split("\n").map { it.trimEnd('\r') }
    var i = 0

    fun addParsed(block: MarkdownBlock, start: Int, end: Int) {
        val safeEnd = end.coerceIn(start, (lines.size - 1).coerceAtLeast(start))
        val raw = lines.subList(start, safeEnd + 1).joinToString("\n")
        blocks.add(ParsedBlock(block, start, safeEnd, raw))
    }

    // Parse Obsidian YAML frontmatter (--- key: value ... ---) into a Properties block
    if (lines.firstOrNull()?.trim() == "---" &&
        lines.getOrNull(1)?.matches(FRONTMATTER_KEY_RE) == true
    ) {
        val close = (1 until lines.size).firstOrNull {
            val t = lines[it].trim()
            t == "---" || t == "..."
        }
        if (close != null) {
            val props = mutableListOf<Pair<String, String>>()
            for (k in 1 until close) {
                val m = FRONTMATTER_KEY_RE.find(lines[k].trim())
                if (m != null) {
                    val key = m.groupValues[1].trim()
                    val value = m.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
                    props.add(key to value)
                }
            }
            if (props.isNotEmpty()) {
                addParsed(MarkdownBlock.Frontmatter(props), 0, close)
            }
            i = close + 1
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Blank
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Obsidian comment block (%% ... %%) — hidden in Preview
        if (trimmed.startsWith("%%")) {
            if (trimmed.length > 2 && trimmed.endsWith("%%") && trimmed.indexOf("%%", 2) == trimmed.length - 2) {
                i++
                continue
            }
            i++
            while (i < lines.size && !lines[i].contains("%%")) {
                i++
            }
            i++ // consume closing %%
            continue
        }

        // Fenced code (``` or ~~~)
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val start = i
            val fence = trimmed.take(3)
            val lang = trimmed.removePrefix(fence).trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            val end = if (i < lines.size) i else lines.size - 1
            addParsed(MarkdownBlock.CodeBlock(lang, code.toString()), start, end)
            i++ // closing fence
            continue
        }

        // Display math: "$$ ... $$" on one line or spread over several. Several equations
        // can be chained with a quadruple dollar ("$$a$$$$b$$"), each is shown on its own line.
        if (trimmed.startsWith("$$")) {
            val start = i
            var j = i
            var acc = trimmed
            var parts = parseDisplayMathParts(acc)
            while (parts == null && j + 1 < lines.size && lines[j + 1].isNotBlank()) {
                j++
                acc += "\n" + lines[j].trim()
                parts = parseDisplayMathParts(acc)
            }
            if (parts != null) {
                addParsed(MarkdownBlock.LatexBlock(parts), start, j)
                i = j + 1
                continue
            }
        }

        // Standalone Obsidian Embed line: ![[Note or Image|Alias]]
        if (trimmed.startsWith("![[") && trimmed.endsWith("]]") && trimmed.indexOf("]]") == trimmed.length - 2) {
            val inner = trimmed.removePrefix("![[").removeSuffix("]]")
            val target = inner.substringBefore('|').trim()
            val alias = inner.substringAfter('|', "").trim().ifEmpty { null }
            addParsed(MarkdownBlock.EmbedBlock(target, alias), i, i)
            i++
            continue
        }

        // Horizontal rule
        if (RULE_RE.matches(trimmed)) {
            addParsed(MarkdownBlock.Divider, i, i)
            i++
            continue
        }

        // Markdown Table: header row followed by | --- | separator row
        if (TABLE_ROW_RE.matches(trimmed) &&
            i + 1 < lines.size &&
            TABLE_SEP_RE.matches(lines[i + 1].trim())
        ) {
            val start = i
            val headers = parseTableCells(trimmed)
            val alignments = parseTableAlignments(lines[i + 1].trim())
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && TABLE_ROW_RE.matches(lines[i].trim())) {
                rows.add(parseTableCells(lines[i].trim()))
                i++
            }
            addParsed(MarkdownBlock.TableBlock(headers, alignments, rows), start, i - 1)
            continue
        }

        // Heading
        val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
        if (headingMatch != null) {
            val cleanHeading = stripInlineCommentsAndBlockId(headingMatch.groupValues[2])
            addParsed(MarkdownBlock.Heading(headingMatch.groupValues[1].length, cleanHeading), i, i)
            i++
            continue
        }

        // Obsidian Callout (> [!type]+ Title) or standard Blockquote
        if (trimmed.startsWith(">")) {
            val start = i
            val calloutMatch = CALLOUT_HEADER_RE.find(trimmed)
            if (calloutMatch != null) {
                val rawType = calloutMatch.groupValues[2].lowercase()
                val foldChar = calloutMatch.groupValues[3].firstOrNull()
                val rawTitle = calloutMatch.groupValues[4].trim()
                val defaultTitle = rawType.replaceFirstChar { it.uppercase() }
                val title = if (rawTitle.isNotEmpty()) stripInlineCommentsAndBlockId(rawTitle) else defaultTitle
                i++
                val bodyLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    bodyLines.add(
                        stripInlineCommentsAndBlockId(
                            lines[i].trim().removePrefix(">").trimStart()
                        )
                    )
                    i++
                }
                addParsed(
                    MarkdownBlock.Callout(
                        type = rawType,
                        title = title,
                        body = bodyLines.joinToString("\n").trim(),
                        foldState = foldChar
                    ),
                    start,
                    i - 1
                )
                continue
            }

            val quote = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quote.add(
                    stripInlineCommentsAndBlockId(
                        lines[i].trim().removePrefix(">").trimStart()
                    )
                )
                i++
            }
            addParsed(MarkdownBlock.Blockquote(quote.joinToString("\n")), start, i - 1)
            continue
        }

        // Footnote definition: [^id]: Text
        val footnoteMatch = FOOTNOTE_DEF_RE.find(trimmed)
        if (footnoteMatch != null) {
            addParsed(
                MarkdownBlock.FootnoteDefinition(
                    id = footnoteMatch.groupValues[1].trim(),
                    text = stripInlineCommentsAndBlockId(footnoteMatch.groupValues[2].trim())
                ),
                i,
                i
            )
            i++
            continue
        }

        // Task item (- [ ], - [x], - [/], - [-], etc.)
        val taskMatch = TASK_LINE_RE.find(line)
        if (taskMatch != null) {
            val statusChar = taskMatch.groupValues[2].firstOrNull() ?: ' '
            val rawTaskText = taskMatch.groupValues[3].trim()
            addParsed(
                MarkdownBlock.TaskItem(
                    lineIndex = i,
                    checked = statusChar != ' ',
                    status = statusChar,
                    text = rawTaskText,
                    indentLevel = indentLevel(taskMatch.groupValues[1])
                ),
                i,
                i
            )
            i++
            continue
        }

        // Bullet
        val bulletMatch = Regex("""^(\s*)[-*+]\s+(.*)$""").find(line)
        if (bulletMatch != null) {
            val cleanText = stripInlineCommentsAndBlockId(bulletMatch.groupValues[2])
            addParsed(
                MarkdownBlock.BulletItem(cleanText, indentLevel(bulletMatch.groupValues[1])),
                i,
                i
            )
            i++
            continue
        }

        // Ordered
        val orderedMatch = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""").find(line)
        if (orderedMatch != null) {
            val cleanText = stripInlineCommentsAndBlockId(orderedMatch.groupValues[3])
            addParsed(
                MarkdownBlock.OrderedItem(
                    number = orderedMatch.groupValues[2].toIntOrNull() ?: 1,
                    text = cleanText,
                    indentLevel = indentLevel(orderedMatch.groupValues[1])
                ),
                i,
                i
            )
            i++
            continue
        }

        // Paragraph: this line is always consumed, then keep going until a new block starts
        val start = i
        val para = mutableListOf(stripInlineCommentsAndBlockId(trimmed))
        i++
        while (i < lines.size && !startsNewBlock(lines[i].trim())) {
            val cleaned = stripInlineCommentsAndBlockId(lines[i].trim())
            if (cleaned.isNotEmpty()) para.add(cleaned)
            i++
        }
        val joined = para.filter { it.isNotEmpty() }.joinToString(" ")
        if (joined.isNotEmpty()) {
            addParsed(MarkdownBlock.Paragraph(joined), start, i - 1)
        }
    }

    return blocks
}

// ============================================================================
// Inline Obsidian Flavored Markdown for Preview
// ============================================================================

private class InlineEnv(
    val orig: String,
    val masked: String,
    val spans: List<MathSpan>,
    val codeBackground: Color,
    val highlightBackground: Color,
    val tagBackground: Color,
    val linkColor: Color,
    val mutedColor: Color,
    val onLinkClick: (String) -> Unit,
    val onWikilinkClick: (String) -> Unit,
    val makeMath: (latex: String, display: Boolean) -> InlineTextContent
) {
    val inline = mutableMapOf<String, InlineTextContent>()
    var lastDisplayEnd = -1
}

private class InlineResult(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

private fun buildInline(
    text: String,
    codeBackground: Color,
    highlightBackground: Color,
    tagBackground: Color,
    linkColor: Color,
    mutedColor: Color,
    onLinkClick: (String) -> Unit,
    onWikilinkClick: (String) -> Unit,
    makeMath: (latex: String, display: Boolean) -> InlineTextContent
): InlineResult {
    val spans = findMathSpans(text)
    val env = InlineEnv(
        orig = text,
        masked = maskSpans(text, spans),
        spans = spans,
        codeBackground = codeBackground,
        highlightBackground = highlightBackground,
        tagBackground = tagBackground,
        linkColor = linkColor,
        mutedColor = mutedColor,
        onLinkClick = onLinkClick,
        onWikilinkClick = onWikilinkClick,
        makeMath = makeMath
    )
    val annotated = buildAnnotatedString { appendRange(env, 0, text.length) }
    return InlineResult(annotated, env.inline)
}

// Handles emphasis / wikilinks / highlights / tags / footnotes / code inside [from, to)
// and recurses into their contents so nested math and styles still render.
private fun AnnotatedString.Builder.appendRange(env: InlineEnv, from: Int, to: Int) {
    var last = from
    for (m in INLINE_RE.findAll(env.masked.substring(from, to))) {
        val a = from + m.range.first
        val b = from + m.range.last + 1
        if (a > last) appendLeaf(env, last, a)
        val g = m.groups
        when {
            // %%comment%% -> stripped in Preview
            g[1] != null -> Unit

            // ![[embed]] inline
            g[2] != null -> {
                val r = g[2]!!.range
                val raw = env.orig.substring(from + r.first, from + r.last + 1)
                val label = formatWikilinkDisplay(raw)
                val link = LinkAnnotation.Clickable(
                    tag = raw,
                    styles = TextLinkStyles(
                        SpanStyle(
                            color = env.linkColor,
                            background = env.tagBackground,
                            fontWeight = FontWeight.Medium
                        )
                    )
                ) { env.onWikilinkClick(raw) }
                withLink(link) {
                    append(" 📎 $label ")
                }
            }

            // [[note]], [[note#heading]], or [[note|alias]]
            g[3] != null -> {
                val r = g[3]!!.range
                val raw = env.orig.substring(from + r.first, from + r.last + 1)
                val display = formatWikilinkDisplay(raw)
                val link = LinkAnnotation.Clickable(
                    tag = raw,
                    styles = TextLinkStyles(
                        SpanStyle(
                            color = env.linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                ) { env.onWikilinkClick(raw) }
                withLink(link) {
                    append(display)
                }
            }

            // ![alt](url) inline image link
            g[4] != null -> {
                val altRange = g[4]!!.range
                val urlRange = g[5]!!.range
                val alt = env.orig.substring(from + altRange.first, from + altRange.last + 1).ifBlank { "Image" }
                val url = env.orig.substring(from + urlRange.first, from + urlRange.last + 1).trim()
                val link = LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(
                        SpanStyle(
                            color = env.linkColor,
                            background = env.tagBackground,
                            fontWeight = FontWeight.Medium
                        )
                    )
                ) { env.onLinkClick(url) }
                withLink(link) {
                    append(" 🖼 $alt ")
                }
            }

            // [label](url)
            g[6] != null -> {
                val label = g[6]!!.range
                val u = g[7]!!.range
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
            g[8] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = env.codeBackground, fontSize = 13.sp)
            ) {
                val r = g[8]!!.range
                append(" ${env.orig.substring(from + r.first, from + r.last + 1)} ")
            }

            // ==highlight==
            g[9] != null -> {
                val r = g[9]!!.range
                withStyle(SpanStyle(background = env.highlightBackground, fontWeight = FontWeight.Medium)) {
                    append(" ")
                    appendRange(env, from + r.first, from + r.last + 1)
                    append(" ")
                }
            }

            // ***bold+italic*** / ___bold+italic___
            g[10] != null || g[11] != null -> {
                val r = (g[10] ?: g[11])!!.range
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    appendRange(env, from + r.first, from + r.last + 1)
                }
            }

            // **bold** / __bold__
            g[12] != null || g[13] != null -> {
                val r = (g[12] ?: g[13])!!.range
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendRange(env, from + r.first, from + r.last + 1)
                }
            }

            // ~~strike~~
            g[14] != null -> {
                val r = g[14]!!.range
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendRange(env, from + r.first, from + r.last + 1)
                }
            }

            // *italic* / _italic_
            g[15] != null || g[16] != null -> {
                val r = (g[15] ?: g[16])!!.range
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendRange(env, from + r.first, from + r.last + 1)
                }
            }

            // [^footnote]
            g[17] != null -> {
                val r = g[17]!!.range
                val fnId = env.orig.substring(from + r.first, from + r.last + 1)
                withStyle(
                    SpanStyle(
                        color = env.linkColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        baselineShift = BaselineShift.Superscript
                    )
                ) {
                    append("[$fnId]")
                }
            }

            // #tag
            g[18] != null -> {
                val r = g[18]!!.range
                val tagName = env.orig.substring(from + r.first, from + r.last + 1)
                withStyle(
                    SpanStyle(
                        color = env.linkColor,
                        background = env.tagBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(" #$tagName ")
                }
            }

            // ^block-id -> hidden in Preview
            g[19] != null -> Unit
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
    modifier: Modifier = Modifier,
    onWikilinkClick: (String) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val fonts = rememberMathFonts()
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val highlightBg = Color(0xFFFFD60A).copy(alpha = 0.32f)
    val linkColor = MaterialTheme.colorScheme.primary
    val tagBg = linkColor.copy(alpha = 0.14f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = if (color != Color.Unspecified) color else LocalContentColor.current
    val fontSizePx = with(density) {
        (if (style.fontSize != TextUnit.Unspecified) style.fontSize else 16.sp).toPx()
    }

    val built = remember(text, codeBg, highlightBg, tagBg, linkColor, mutedColor, uriHandler, onWikilinkClick, fonts, fontSizePx, textColor, density) {
        val argb = textColor.toArgb()
        buildInline(
            text = text,
            codeBackground = codeBg,
            highlightBackground = highlightBg,
            tagBackground = tagBg,
            linkColor = linkColor,
            mutedColor = mutedColor,
            onLinkClick = { url -> runCatching { uriHandler.openUri(url) } },
            onWikilinkClick = onWikilinkClick
        ) { latex, display ->
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
// Callout styling helpers (13 official Obsidian types + aliases)
// ============================================================================

private fun calloutAccentColor(type: String): Color = when (type.lowercase()) {
    "note", "info", "todo" -> Color(0xFF448AFF)
    "abstract", "summary", "tldr", "tip", "hint", "important" -> Color(0xFF53DFDD)
    "success", "check", "done" -> Color(0xFF44CF6E)
    "question", "help", "faq", "warning", "caution", "attention" -> Color(0xFFE9973F)
    "failure", "fail", "missing", "danger", "error", "bug" -> Color(0xFFFB464C)
    "example" -> Color(0xFFA882FF)
    "quote", "cite" -> Color(0xFF9E9E9E)
    else -> Color(0xFF448AFF) // Unrecognized types default to Note style in Obsidian
}

private fun calloutIcon(type: String): ImageVector = when (type.lowercase()) {
    "note" -> CalloutIcons.Note
    "abstract", "summary", "tldr" -> CalloutIcons.Abstract
    "info" -> CalloutIcons.Info
    "todo" -> CalloutIcons.Todo
    "tip", "hint", "important" -> CalloutIcons.Tip
    "success", "check", "done" -> CalloutIcons.Success
    "question", "help", "faq" -> CalloutIcons.Question
    "warning", "caution", "attention" -> CalloutIcons.Warning
    "failure", "fail", "missing" -> CalloutIcons.Failure
    "danger", "error" -> CalloutIcons.Danger
    "bug" -> CalloutIcons.Bug
    "example" -> CalloutIcons.Example
    "quote", "cite" -> CalloutIcons.Quote
    else -> CalloutIcons.Note // Unrecognized types default to Note icon (lucide-pencil) in Obsidian
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