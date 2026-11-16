package com.example.dwell.sidebar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
// Precision Top Glass Bar Icons (Folder, Plus, Search, 8-Tooth Settings Gear)
// ============================================================================

private fun PathBuilder.addExactCircle(cx: Float, cy: Float, r: Float) {
    // 4-segment cubic Bezier circle (avoids Android arcTo 180-degree distortion)
    val k = 0.55228475f * r
    moveTo(cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    close()
}

private val TopBarFolderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TopBarFolder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Purely outlined (stroke-only, no solid fill) folder matching the Find icon
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3.2f, 7.2f)
            lineTo(3.2f, 6.4f)
            quadTo(3.2f, 4.6f, 5.0f, 4.6f)
            lineTo(9.2f, 4.6f)
            quadTo(10.3f, 4.6f, 11.0f, 5.6f)
            lineTo(12.2f, 7.2f)
            lineTo(19.0f, 7.2f)
            quadTo(20.8f, 7.2f, 20.8f, 9.0f)
            lineTo(20.8f, 17.2f)
            quadTo(20.8f, 19.2f, 18.8f, 19.2f)
            lineTo(5.2f, 19.2f)
            quadTo(3.2f, 19.2f, 3.2f, 17.2f)
            close()
        }
    }.build()
}

private val TopBarPlusIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TopBarPlus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 4.8f)
            lineTo(12f, 19.2f)
            moveTo(4.8f, 12f)
            lineTo(19.2f, 12f)
        }
    }.build()
}

private val TopBarSearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TopBarSearch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            addExactCircle(10.6f, 10.6f, 5.8f)
            moveTo(14.8f, 14.8f)
            lineTo(19.6f, 19.6f)
        }
    }.build()
}

private val TopBarSettingsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TopBarSettings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.65f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Radially symmetric 8-tooth gear contour centered at (12, 12)
            val cx = 12f
            val cy = 12f
            val rOuter = 8.7f
            val rInner = 6.55f
            val teeth = 8
            val stepDeg = 360.0 / teeth
            val tipHalfDeg = 9.0
            val rootShoulderDeg = 13.5

            fun pt(deg: Double, r: Float): Pair<Float, Float> {
                val rad = deg * PI / 180.0
                return (cx + r * cos(rad).toFloat()) to (cy + r * sin(rad).toFloat())
            }

            for (i in 0 until teeth) {
                val centerDeg = i * stepDeg
                val p1 = pt(centerDeg - rootShoulderDeg, rInner)
                val p2 = pt(centerDeg - tipHalfDeg, rOuter)
                val p3 = pt(centerDeg + tipHalfDeg, rOuter)
                val p4 = pt(centerDeg + rootShoulderDeg, rInner)
                val nextValleyMid = pt(centerDeg + stepDeg / 2.0, rInner * 0.985f)
                val nextStart = pt(centerDeg + stepDeg - rootShoulderDeg, rInner)

                if (i == 0) {
                    moveTo(p1.first, p1.second)
                }
                lineTo(p2.first, p2.second)
                lineTo(p3.first, p3.second)
                lineTo(p4.first, p4.second)
                quadTo(
                    nextValleyMid.first,
                    nextValleyMid.second,
                    nextStart.first,
                    nextStart.second
                )
            }
            close()

            // True circular center hole
            addExactCircle(cx, cy, 3.0f)
        }
    }.build()
}

// ============================================================================
// Custom Stroke Icons for Hovering Menus & Long-Press Context Dialogs
// ============================================================================

private fun buildMenuStrokeIcon(
    name: String,
    strokeWidth: Float = 1.95f,
    block: PathBuilder.() -> Unit
): ImageVector =
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

// Matches "New Note" icon (rounded square with diagonal pen)
private val NewNoteMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("NewNoteMenu") {
        moveTo(11f, 4f)
        lineTo(6.5f, 4f)
        quadTo(4f, 4f, 4f, 6.5f)
        lineTo(4f, 17.5f)
        quadTo(4f, 20f, 6.5f, 20f)
        lineTo(17.5f, 20f)
        quadTo(20f, 20f, 20f, 17.5f)
        lineTo(20f, 13f)

        moveTo(17.6f, 3.4f)
        quadTo(19f, 2f, 20.4f, 3.4f)
        lineTo(20.6f, 3.6f)
        quadTo(22f, 5f, 20.6f, 6.4f)
        lineTo(12.2f, 14.8f)
        lineTo(8.8f, 15.2f)
        lineTo(9.2f, 11.8f)
        close()
    }
}

// Matches "New Folder" icon (outlined folder with centered +)
private val NewFolderMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("NewFolderMenu") {
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

        moveTo(12f, 10.6f)
        lineTo(12f, 16.2f)
        moveTo(9.2f, 13.4f)
        lineTo(14.8f, 13.4f)
    }
}

// Matches uploaded "Pin" icon (vertical pushpin)
internal val PinMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("PinMenu", strokeWidth = 2.0f) {
        // Pushpin silhouette: flat top cap, concave waist, flared lower base
        moveTo(8f, 3.5f)
        lineTo(16f, 3.5f)
        quadTo(17f, 3.5f, 17f, 4.5f)
        quadTo(17f, 5.5f, 15.2f, 6.2f)
        lineTo(14.5f, 10.5f)
        lineTo(17.2f, 13.2f)
        quadTo(18f, 14f, 18f, 15f)
        lineTo(6f, 15f)
        quadTo(6f, 14f, 6.8f, 13.2f)
        lineTo(9.5f, 10.5f)
        lineTo(8.8f, 6.2f)
        quadTo(7f, 5.5f, 7f, 4.5f)
        quadTo(7f, 3.5f, 8f, 3.5f)
        close()
        // Bottom pin needle
        moveTo(12f, 15f)
        lineTo(12f, 21f)
    }
}

// Matches uploaded "Hide" icon (eye with diagonal slash)
private val HideMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("HideMenu", strokeWidth = 2.0f) {
        // Diagonal slash from top-left to bottom-right
        moveTo(3f, 3f)
        lineTo(21f, 21f)
        // Upper right eye contour
        moveTo(10.6f, 5.2f)
        quadTo(11.3f, 5f, 12f, 5f)
        curveTo(16.8f, 5f, 20.4f, 8.2f, 21.8f, 12f)
        quadTo(20.9f, 14.4f, 18.8f, 16.2f)
        // Lower left eye contour
        moveTo(6.4f, 6.6f)
        quadTo(3.6f, 8.8f, 2.2f, 12f)
        curveTo(3.6f, 15.8f, 7.2f, 19f, 12f, 19f)
        quadTo(14.5f, 19f, 16.6f, 17.8f)
        // Inner pupil arc
        moveTo(9.9f, 9.9f)
        quadTo(9f, 10.8f, 9f, 12f)
        quadTo(9f, 15f, 12f, 15f)
        quadTo(13.2f, 15f, 14.1f, 14.1f)
    }
}

// Matches uploaded "Rename" icon (diagonal pencil with bottom-right baseline)
private val RenameMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("RenameMenu", strokeWidth = 2.0f) {
        // Diagonal pencil with rounded eraser cap and pointed tip
        moveTo(16.2f, 3.8f)
        quadTo(17.8f, 2.2f, 19.4f, 3.8f)
        lineTo(20.2f, 4.6f)
        quadTo(21.8f, 6.2f, 20.2f, 7.8f)
        lineTo(8.5f, 19.5f)
        lineTo(4f, 20.5f)
        lineTo(5f, 16f)
        close()
        // Short horizontal baseline to the right of the tip
        moveTo(12.5f, 20.5f)
        lineTo(20.5f, 20.5f)
    }
}

// Matches uploaded "Make a copy" icon (overlapping dog-eared document pages)
private val MakeCopyMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("MakeCopyMenu", strokeWidth = 2.0f) {
        // Front page with folded top-right dog-ear corner
        moveTo(15f, 3f)
        lineTo(10.5f, 3f)
        quadTo(8.5f, 3f, 8.5f, 5f)
        lineTo(8.5f, 15f)
        quadTo(8.5f, 17f, 10.5f, 17f)
        lineTo(18f, 17f)
        quadTo(20f, 17f, 20f, 15f)
        lineTo(20f, 8f)
        close()
        // Dog-ear crease
        moveTo(15f, 3f)
        lineTo(15f, 8f)
        lineTo(20f, 8f)
        // Back page peeking out on the left and bottom
        moveTo(8.5f, 7f)
        lineTo(6f, 7f)
        quadTo(4f, 7f, 4f, 9f)
        lineTo(4f, 19f)
        quadTo(4f, 21f, 6f, 21f)
        lineTo(14f, 21f)
        quadTo(16f, 21f, 16f, 19f)
        lineTo(16f, 17f)
    }
}

// "Open in new tab" icon (window/tab pane with +)
private val OpenInNewTabMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("OpenInNewTabMenu", strokeWidth = 1.95f) {
        // Outer rounded window/tab frame
        moveTo(12f, 4f)
        lineTo(6.5f, 4f)
        quadTo(4f, 4f, 4f, 6.5f)
        lineTo(4f, 17.5f)
        quadTo(4f, 20f, 6.5f, 20f)
        lineTo(17.5f, 20f)
        quadTo(20f, 20f, 20f, 17.5f)
        lineTo(20f, 12f)
        // Top-right "+" badge
        moveTo(17.5f, 3.5f)
        lineTo(17.5f, 9.5f)
        moveTo(14.5f, 6.5f)
        lineTo(20.5f, 6.5f)
    }
}

// "Color" icon for Folder menu
private val ColorPaletteMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("ColorPaletteMenu", strokeWidth = 1.95f) {
        // Droplet / palette circle contour
        moveTo(12f, 3f)
        quadTo(19f, 10f, 19f, 14.5f)
        curveTo(19f, 18.4f, 15.9f, 21f, 12f, 21f)
        curveTo(8.1f, 21f, 5f, 18.4f, 5f, 14.5f)
        quadTo(5f, 10f, 12f, 3f)
        close()
    }
}

// "Delete" trash icon
private val DeleteMenuIcon: ImageVector by lazy {
    buildMenuStrokeIcon("DeleteMenu", strokeWidth = 1.95f) {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        moveTo(9f, 6f)
        lineTo(9f, 4f)
        quadTo(9f, 3f, 10f, 3f)
        lineTo(14f, 3f)
        quadTo(15f, 3f, 15f, 4f)
        lineTo(15f, 6f)
        moveTo(6f, 6f)
        lineTo(6.8f, 19.2f)
        quadTo(6.9f, 21f, 8.7f, 21f)
        lineTo(15.3f, 21f)
        quadTo(17.1f, 21f, 17.2f, 19.2f)
        lineTo(18f, 6f)
        moveTo(10f, 10f)
        lineTo(10f, 17f)
        moveTo(14f, 10f)
        lineTo(14f, 17f)
    }
}

// ============================================================================
// Standard Folder Color Options
// ============================================================================

private data class FolderColorOption(
    val id: String,
    val label: String,
    val color: Color? // null = Accent (Default)
)

private val FOLDER_COLOR_OPTIONS = listOf(
    FolderColorOption("accent", "Accent (Default)", null),
    FolderColorOption("red", "Red", Color(0xFFEF4444)),
    FolderColorOption("blue", "Blue", Color(0xFF3B82F6)),
    FolderColorOption("green", "Green", Color(0xFF22C55E)),
    FolderColorOption("orange", "Orange", Color(0xFFF97316)),
    FolderColorOption("yellow", "Yellow", Color(0xFFEAB308)),
    FolderColorOption("teal", "Teal", Color(0xFF14B8A6)),
    FolderColorOption("purple", "Purple", Color(0xFFA855F7)),
    FolderColorOption("pink", "Pink", Color(0xFFEC4899))
)

// ============================================================================
// Tree Data Structures
// ============================================================================

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
        override val depth: Int,
        val isPinned: Boolean
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
    expandedPaths: Set<String>,
    pinnedPaths: Set<String>
): List<SidebarTreeEntry> {
    val out = mutableListOf<SidebarTreeEntry>()

    fun visit(node: SidebarFolderNode, depth: Int) {
        val sortedFolders = node.subFolders.values.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        // Pinned notes appear first in their folder, then alphabetical
        val sortedFiles = node.files.sortedWith { a, b ->
            val aPinned = a.relativePath in pinnedPaths
            val bPinned = b.relativePath in pinnedPaths
            if (aPinned != bPinned) {
                if (aPinned) -1 else 1
            } else {
                String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
            }
        }

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
            out.add(
                SidebarTreeEntry.NoteEntry(
                    note = file,
                    depth = depth,
                    isPinned = file.relativePath in pinnedPaths
                )
            )
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
// Persistence Helpers (Pinned Notes, Hidden Notes, Folder Colors, Open Tabs)
// ============================================================================

private const val PREF_HIDDEN_NOTES = "sidebar_hidden_notes"
private const val PREF_FOLDER_COLORS = "sidebar_folder_colors"

private fun loadStringSetPref(prefs: android.content.SharedPreferences, key: String): Set<String> =
    prefs.getString(key, null)
        ?.split("\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()

private fun saveStringSetPref(prefs: android.content.SharedPreferences, key: String, values: Set<String>) {
    prefs.edit {
        if (values.isEmpty()) remove(key) else putString(key, values.joinToString("\n"))
    }
}

private fun loadFolderColorsPref(prefs: android.content.SharedPreferences): Map<String, Int> {
    val raw = prefs.getString(PREF_FOLDER_COLORS, null) ?: return emptyMap()
    val out = mutableMapOf<String, Int>()
    for (line in raw.split("\n")) {
        val idx = line.lastIndexOf('=')
        if (idx > 0) {
            val path = line.substring(0, idx).trim()
            val argb = line.substring(idx + 1).trim().toIntOrNull()
            if (path.isNotEmpty() && argb != null) {
                out[path] = argb
            }
        }
    }
    return out
}

private fun saveFolderColorsPref(prefs: android.content.SharedPreferences, map: Map<String, Int>) {
    prefs.edit {
        if (map.isEmpty()) {
            remove(PREF_FOLDER_COLORS)
        } else {
            putString(
                PREF_FOLDER_COLORS,
                map.entries.joinToString("\n") { "${it.key}=${it.value}" }
            )
        }
    }
}

private fun addUriToSavedOpenTabs(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val current = prefs.getString("open_tab_uris", null)
        ?.split("\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
    val uriStr = uri.toString()
    if (uriStr !in current) {
        val updated = current + uriStr
        prefs.edit { putString("open_tab_uris", updated.joinToString("\n")) }
    }
}

// ============================================================================
// Storage Access Framework (SAF) Operations for Files & Folders
// ============================================================================

internal fun resolveVaultTreeUri(context: Context, notes: List<NoteFile>): Uri? {
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
    rawInputPath: String,
    initialContent: String? = null
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

        val body = initialContent ?: "# ${fileName.removeSuffix(".md")}\n\n"
        resolver.openOutputStream(createdUri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(body)
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

internal suspend fun copyNoteInVault(
    context: Context,
    treeUri: Uri,
    existingItems: List<NoteFile>,
    sourceNote: NoteFile
): NoteFile? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val content = resolver.openInputStream(sourceNote.uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()

        val parentPrefix = if (sourceNote.relativePath.contains('/')) {
            sourceNote.relativePath.substringBeforeLast('/')
        } else {
            ""
        }
        val baseStem = sourceNote.name.removeSuffix(".md")
        val ext = if (sourceNote.name.endsWith(".md", ignoreCase = true)) ".md" else ""

        var candidateNum = 1
        var copyName: String
        var copyRelPath: String
        val existingPaths = existingItems.map { it.relativePath.lowercase() }.toSet()
        while (true) {
            val suffix = if (candidateNum == 1) " (Copy)" else " (Copy $candidateNum)"
            copyName = "$baseStem$suffix$ext"
            copyRelPath = if (parentPrefix.isEmpty()) copyName else "$parentPrefix/$copyName"
            if (copyRelPath.lowercase() !in existingPaths) break
            candidateNum++
        }

        createNoteInVault(
            context = context,
            treeUri = treeUri,
            existingItems = existingItems,
            rawInputPath = copyRelPath,
            initialContent = content
        )
    }.getOrNull()
}

private suspend fun copyFolderInVault(
    context: Context,
    treeUri: Uri,
    existingItems: List<NoteFile>,
    folderPath: String
): List<NoteFile> = withContext(Dispatchers.IO) {
    val createdList = mutableListOf<NoteFile>()
    runCatching {
        val existingPaths = existingItems.map { it.relativePath.lowercase() }.toSet()
        var candidateNum = 1
        var targetFolderPath: String
        while (true) {
            val suffix = if (candidateNum == 1) " (Copy)" else " (Copy $candidateNum)"
            targetFolderPath = "$folderPath$suffix"
            if (targetFolderPath.lowercase() !in existingPaths) break
            candidateNum++
        }

        val workingItems = existingItems.toMutableList()
        val rootCopyDir = createFolderInVault(context, treeUri, workingItems, targetFolderPath)
        if (rootCopyDir != null) {
            createdList.add(rootCopyDir)
            workingItems.add(rootCopyDir)
        }

        val prefix = "$folderPath/"
        val descendants = existingItems
            .filter { it.relativePath.startsWith(prefix, ignoreCase = true) }
            .sortedBy { it.relativePath.length }

        val resolver = context.contentResolver
        for (child in descendants) {
            val subRel = child.relativePath.substring(prefix.length)
            val newChildPath = "$targetFolderPath/$subRel"
            if (child.isDirectory) {
                val copiedDir = createFolderInVault(context, treeUri, workingItems, newChildPath)
                if (copiedDir != null) {
                    createdList.add(copiedDir)
                    workingItems.add(copiedDir)
                }
            } else {
                val text = resolver.openInputStream(child.uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                val copiedNote = createNoteInVault(context, treeUri, workingItems, newChildPath, text)
                if (copiedNote != null) {
                    createdList.add(copiedNote)
                    workingItems.add(copiedNote)
                }
            }
        }
    }
    createdList
}

internal suspend fun renameNoteInVault(
    context: Context,
    note: NoteFile,
    newRawName: String
): NoteFile? = withContext(Dispatchers.IO) {
    runCatching {
        val clean = newRawName.trim().trim('/')
        if (clean.isEmpty()) return@withContext null
        val newFileName = if (clean.contains('.')) clean else "$clean.md"
        val renamedUri = DocumentsContract.renameDocument(
            context.contentResolver,
            note.uri,
            newFileName
        ) ?: return@withContext null

        val parentPrefix = if (note.relativePath.contains('/')) {
            note.relativePath.substringBeforeLast('/')
        } else {
            ""
        }
        val newRelPath = if (parentPrefix.isEmpty()) newFileName else "$parentPrefix/$newFileName"
        NoteFile(
            uri = renamedUri,
            name = newFileName,
            relativePath = newRelPath,
            isDirectory = false
        )
    }.getOrNull()
}

private suspend fun renameFolderInVault(
    context: Context,
    treeUri: Uri,
    existingItems: List<NoteFile>,
    folderPath: String,
    newFolderName: String
): Pair<String, Uri>? = withContext(Dispatchers.IO) {
    runCatching {
        val cleanName = newFolderName.trim().trim('/')
        if (cleanName.isEmpty()) return@withContext null
        val folderItem = existingItems.firstOrNull {
            it.isDirectory && it.relativePath.equals(folderPath, ignoreCase = true)
        }
        val dirUri = folderItem?.uri ?: run {
            val segments = folderPath.split('/').filter { it.isNotEmpty() }
            ensureSubfolderDocumentUri(context.contentResolver, treeUri, existingItems, segments)
        } ?: return@withContext null

        val renamedUri = DocumentsContract.renameDocument(
            context.contentResolver,
            dirUri,
            cleanName
        ) ?: return@withContext null

        val parentPrefix = if (folderPath.contains('/')) {
            folderPath.substringBeforeLast('/')
        } else {
            ""
        }
        val newPath = if (parentPrefix.isEmpty()) cleanName else "$parentPrefix/$cleanName"
        newPath to renamedUri
    }.getOrNull()
}

internal suspend fun deleteDocumentInVault(
    context: Context,
    uri: Uri
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    }.getOrDefault(false)
}

private fun ensureSubfolderDocumentUri(
    resolver: ContentResolver,
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
            // Query existing child directory before creating
            val parentDocId = DocumentsContract.getDocumentId(currentDocUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            var foundUri: Uri? = null
            runCatching {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                            name.equals(seg, ignoreCase = true)
                        ) {
                            foundUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            break
                        }
                    }
                }
            }
            foundUri ?: DocumentsContract.createDocument(
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
// Top Spotlight Glass Pill Bar (matching 31884.gif style) + Hovering Plus Menu
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
    val accentColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if (isDark) {
        Color.White.copy(alpha = 0.52f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
    }
    val density = LocalDensity.current

    // Determine active slot index (0 = Files, 1 = Plus, 2 = Search, 3 = Settings)
    val activeSlotIndex = when {
        showPlusMenu || selectedAction == NotesSidebarAction.NEW_NOTE -> 1
        selectedAction == NotesSidebarAction.SEARCH -> 2
        selectedAction == NotesSidebarAction.SETTINGS -> 3
        else -> 0
    }

    val animatedSlot by animateFloatAsState(
        targetValue = activeSlotIndex.toFloat(),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "spotlightSlotAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .height(48.dp)
            .drawWithCache {
                val r = size.height / 2f
                val outerCorner = CornerRadius(r, r)
                val hPad = 5.dp.toPx()
                val vPad = 4.dp.toPx()
                val innerW = (size.width - hPad * 2f).coerceAtLeast(1f)
                val slotW = innerW / 4f
                val pillW = slotW
                val pillH = (size.height - vPad * 2f).coerceAtLeast(1f)
                val pillR = pillH / 2f
                val pillCorner = CornerRadius(pillR, pillR)

                val pillLeft = hPad + animatedSlot * slotW
                val centerX = pillLeft + pillW / 2f

                val clipOutline = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, outerCorner))
                }

                // 1. Matte dark smoky glass track fill (matching 31884.gif)
                val trackFillBrush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF1B1C1E).copy(alpha = 0.92f),
                            Color(0xFF121315).copy(alpha = 0.96f)
                        )
                    } else {
                        listOf(
                            Color(0xFFECEEF2).copy(alpha = 0.94f),
                            Color(0xFFE2E5EB).copy(alpha = 0.97f)
                        )
                    }
                )

                // 2. Ambient radial bottom spotlight glow centered under the active tab
                val spotlightRadius = (slotW * 1.15f).coerceAtLeast(40f)
                val bottomSpotlightBrush = Brush.radialGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            accentColor.copy(alpha = 0.24f),
                            accentColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    },
                    center = Offset(centerX, size.height + 4.dp.toPx()),
                    radius = spotlightRadius
                )

                // 3. Sliding active inner capsule (lit from the bottom spotlight)
                val activeCapsuleFill = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.22f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.95f)
                        )
                    },
                    startY = vPad,
                    endY = vPad + pillH
                )

                val activeCapsuleBorder = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.28f)
                        )
                    } else {
                        listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.16f)
                        )
                    },
                    startY = vPad,
                    endY = vPad + pillH
                )

                // 4. Outer 1.dp rim with bright hotspot directly beneath & above the active capsule
                val normCenter = (centerX / size.width.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f)
                val spread = 0.22f
                val leftStop = (normCenter - spread).coerceAtLeast(0.0f)
                val rightStop = (normCenter + spread).coerceAtMost(1.0f)

                val baseRimColor = if (isDark) {
                    Color.White.copy(alpha = 0.09f)
                } else {
                    Color.Black.copy(alpha = 0.11f)
                }
                val hotRimColor = if (isDark) {
                    Color.White.copy(alpha = 0.55f)
                } else {
                    accentColor.copy(alpha = 0.45f)
                }

                val rimHotspotBrush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to baseRimColor,
                        leftStop to baseRimColor,
                        normCenter to hotRimColor,
                        rightStop to baseRimColor,
                        1.0f to baseRimColor
                    )
                )

                val borderStrokePx = 1.dp.toPx()
                val innerStrokePx = 0.85.dp.toPx()

                onDrawBehind {
                    // Soft ambient under-glow right below the active slot
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, size.height),
                            radius = slotW * 0.85f
                        ),
                        radius = slotW * 0.85f,
                        center = Offset(centerX, size.height)
                    )

                    // Clipped interior: dark track + sliding capsule + bottom spotlight
                    clipPath(clipOutline) {
                        drawRoundRect(
                            brush = trackFillBrush,
                            cornerRadius = outerCorner
                        )

                        // Sliding inner pill capsule
                        drawRoundRect(
                            brush = activeCapsuleFill,
                            topLeft = Offset(pillLeft, vPad),
                            size = Size(pillW, pillH),
                            cornerRadius = pillCorner
                        )
                        drawRoundRect(
                            brush = activeCapsuleBorder,
                            topLeft = Offset(pillLeft, vPad),
                            size = Size(pillW, pillH),
                            cornerRadius = pillCorner,
                            style = Stroke(width = innerStrokePx)
                        )

                        // Concentrated bottom spotlight washing upward over the active pill
                        drawRect(brush = bottomSpotlightBrush)
                    }

                    // Outer perimeter rim with localized spotlight hotspot
                    drawRoundRect(
                        brush = rimHotspotBrush,
                        cornerRadius = outerCorner,
                        style = Stroke(width = borderStrokePx)
                    )
                }
            }
            .padding(horizontal = 5.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassyBarIconItem(
                icon = TopBarFolderIcon,
                contentDescription = "Files",
                selected = activeSlotIndex == 0,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                onClick = { onSelectAction(NotesSidebarAction.FILES) },
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                GlassyBarIconItem(
                    icon = TopBarPlusIcon,
                    contentDescription = "Create new note or folder",
                    selected = activeSlotIndex == 1,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor,
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
                icon = TopBarSearchIcon,
                contentDescription = "Search notes",
                selected = activeSlotIndex == 2,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                onClick = { onSelectAction(NotesSidebarAction.SEARCH) },
                modifier = Modifier.weight(1f)
            )
            GlassyBarIconItem(
                icon = TopBarSettingsIcon,
                contentDescription = "Settings",
                selected = activeSlotIndex == 3,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
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
    ContextPopupCard(width = 184.dp) {
        ContextMenuOptionRow(
            icon = NewNoteMenuIcon,
            label = "New Note",
            onClick = onNewNote
        )
        ContextMenuOptionRow(
            icon = NewFolderMenuIcon,
            label = "New Folder",
            onClick = onNewFolder
        )
    }
}

// ============================================================================
// Hovering Long-Press Popup Menus for File & Folder Items
// ============================================================================

@Composable
private fun ContextPopupCard(
    width: androidx.compose.ui.unit.Dp = 216.dp,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val menuBg = if (isDark) {
        Color(0xFF141614).copy(alpha = 0.98f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.99f)
    }
    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.08f)
            )
        } else {
            listOf(
                Color.Black.copy(alpha = 0.16f),
                Color.Black.copy(alpha = 0.08f)
            )
        }
    )
    val shape = RoundedCornerShape(10.dp)

    Surface(
        shape = shape,
        color = menuBg,
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        modifier = Modifier
            .width(width)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ContextMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun ContextMenuOptionRow(
    icon: ImageVector,
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            fontWeight = FontWeight.Normal,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun FileHoverPopupMenu(
    offset: IntOffset,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onMakeCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    val deleteRed = Color(0xFFEF4444)

    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        ContextPopupCard(width = 212.dp) {
            ContextMenuOptionRow(
                icon = OpenInNewTabMenuIcon,
                label = "Open in new tab",
                onClick = onOpenInNewTab
            )
            ContextMenuOptionRow(
                icon = MakeCopyMenuIcon,
                label = "Make a copy",
                onClick = onMakeCopy
            )
            ContextMenuDivider()
            ContextMenuOptionRow(
                icon = PinMenuIcon,
                label = if (isPinned) "Unpin" else "Pin",
                onClick = onTogglePin
            )
            ContextMenuOptionRow(
                icon = HideMenuIcon,
                label = "Hide",
                onClick = onHide
            )
            ContextMenuDivider()
            ContextMenuOptionRow(
                icon = RenameMenuIcon,
                label = "Rename",
                onClick = onRename
            )
            // Delete: red text & icon on a normal transparent option row
            ContextMenuOptionRow(
                icon = DeleteMenuIcon,
                label = "Delete",
                textColor = deleteRed,
                iconTint = deleteRed,
                onClick = onDelete
            )
        }
    }
}

// Long-press menu on an open note tab (same look as the file menu in the sidebar). "Close" is left
// out for pinned tabs: they are locked until unpinned.
@Composable
internal fun TabHoverPopupMenu(
    offset: IntOffset,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onMakeCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val deleteRed = Color(0xFFEF4444)

    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        ContextPopupCard(width = 212.dp) {
            if (!isPinned) {
                ContextMenuOptionRow(
                    icon = Icons.Default.Close,
                    label = "Close tab",
                    onClick = onClose
                )
            }
            ContextMenuOptionRow(
                icon = MakeCopyMenuIcon,
                label = "Make a copy",
                onClick = onMakeCopy
            )
            ContextMenuDivider()
            ContextMenuOptionRow(
                icon = PinMenuIcon,
                label = if (isPinned) "Unpin" else "Pin",
                onClick = onTogglePin
            )
            ContextMenuDivider()
            ContextMenuOptionRow(
                icon = RenameMenuIcon,
                label = "Rename",
                onClick = onRename
            )
            ContextMenuOptionRow(
                icon = DeleteMenuIcon,
                label = "Delete",
                textColor = deleteRed,
                iconTint = deleteRed,
                onClick = onDelete
            )
        }
    }
}

@Composable
internal fun DeleteNoteDialog(
    noteName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete note?", fontWeight = FontWeight.SemiBold) },
        text = { Text("\"$noteName\" will be permanently deleted.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
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
private fun FolderHoverPopupMenu(
    offset: IntOffset,
    currentFolderColorArgb: Int?,
    onDismiss: () -> Unit,
    onNewNote: () -> Unit,
    onNewFolder: () -> Unit,
    onSelectColor: (Color?) -> Unit,
    onMakeCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var colorExpanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (colorExpanded) 0f else -90f,
        label = "colorSubmenuChevron"
    )
    val accentColor = MaterialTheme.colorScheme.primary
    val deleteRed = Color(0xFFEF4444)

    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        ContextPopupCard(width = 220.dp) {
            ContextMenuOptionRow(
                icon = NewNoteMenuIcon,
                label = "New note",
                onClick = onNewNote
            )
            ContextMenuOptionRow(
                icon = NewFolderMenuIcon,
                label = "New folder",
                onClick = onNewFolder
            )
            ContextMenuDivider()
            ContextMenuOptionRow(
                icon = ColorPaletteMenuIcon,
                label = "Color",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val previewColor = currentFolderColorArgb?.let { Color(it) } ?: accentColor
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(previewColor)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(chevronRotation)
                        )
                    }
                },
                onClick = { colorExpanded = !colorExpanded }
            )

            AnimatedVisibility(visible = colorExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    FOLDER_COLOR_OPTIONS.forEach { option ->
                        val swatchColor = option.color ?: accentColor
                        val isSelected = if (option.color == null) {
                            currentFolderColorArgb == null
                        } else {
                            currentFolderColorArgb == option.color.toArgb()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectColor(option.color) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            ContextMenuOptionRow(
                icon = MakeCopyMenuIcon,
                label = "Make a copy",
                onClick = onMakeCopy
            )
            ContextMenuDivider()
            ContextMenuOptionRow(
                icon = RenameMenuIcon,
                label = "Rename",
                onClick = onRename
            )
            // Delete: red text & icon on a normal transparent option row
            ContextMenuOptionRow(
                icon = DeleteMenuIcon,
                label = "Delete",
                textColor = deleteRed,
                iconTint = deleteRed,
                onClick = onDelete
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) accentColor else inactiveColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun NameInputDialog(
    title: String,
    label: String,
    placeholder: String,
    initialValue: String = "",
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotesSidebarContent(
    folderName: String,
    hasFolder: Boolean,
    notes: List<NoteFile>,
    selectedNoteUri: Uri?,
    onSelectNote: (NoteFile) -> Unit,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit = {},
    pinnedPaths: Set<String> = emptySet(),
    onTogglePin: (NoteFile) -> Unit = {},
    onNoteRenamed: (NoteFile, NoteFile) -> Unit = { _, _ -> },
    onNoteDeleted: (NoteFile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var activeAction by rememberSaveable { mutableStateOf(NotesSidebarAction.FILES) }
    var showPlusMenu by rememberSaveable { mutableStateOf(false) }
    var newNoteParentFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewNoteDialog by rememberSaveable { mutableStateOf(false) }
    var newFolderParentFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewFolderDialog by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Long-press context dialog targets
    var contextMenuNote by remember { mutableStateOf<NoteFile?>(null) }
    var contextMenuFolder by remember { mutableStateOf<SidebarTreeEntry.FolderEntry?>(null) }
    var renamingNote by remember { mutableStateOf<NoteFile?>(null) }
    var renamingFolder by remember { mutableStateOf<SidebarTreeEntry.FolderEntry?>(null) }
    var deletingNote by remember { mutableStateOf<NoteFile?>(null) }
    var deletingFolder by remember { mutableStateOf<SidebarTreeEntry.FolderEntry?>(null) }

    // Pinned notes live in NoteViewModel (they are also pinned in the tab bar); hidden notes and
    // folder colors are persisted here
    var hiddenPaths by remember { mutableStateOf(loadStringSetPref(prefs, PREF_HIDDEN_NOTES)) }
    var folderColors by remember { mutableStateOf(loadFolderColorsPref(prefs)) }

    // Local mutations so create/copy/rename/delete reflect instantaneously in the sidebar tree
    val locallyAddedItems = remember(folderName) { mutableStateListOf<NoteFile>() }
    val locallyRemovedPaths = remember(folderName) { mutableStateListOf<String>() }
    val locallyRenamedMap = remember(folderName) { mutableStateMapOf<String, NoteFile>() }

    val allNotes = remember(
        notes,
        locallyAddedItems.toList(),
        locallyRemovedPaths.toList(),
        locallyRenamedMap.toMap(),
        hiddenPaths
    ) {
        val removedSet = locallyRemovedPaths.map { it.lowercase() }.toSet()
        val base = notes.mapNotNull { item ->
            val lower = item.relativePath.lowercase()
            if (lower in removedSet || removedSet.any { lower.startsWith("$it/") }) {
                null
            } else {
                locallyRenamedMap[item.relativePath] ?: item
            }
        }
        val existingPaths = base.map { it.relativePath.lowercase() }.toSet()
        val extra = locallyAddedItems.filter {
            val lower = it.relativePath.lowercase()
            lower !in existingPaths && lower !in removedSet && !removedSet.any { r -> lower.startsWith("$r/") }
        }
        (base + extra)
            .filter { it.relativePath !in hiddenPaths }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })
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

    // ---- Create New Note Dialog ----
    if (showNewNoteDialog) {
        val parentPrefix = newNoteParentFolder
        NameInputDialog(
            title = if (parentPrefix != null) "New Note in $parentPrefix" else "New Note",
            label = "Note name",
            placeholder = "e.g. Daily Note",
            confirmText = "Create",
            onDismiss = {
                showNewNoteDialog = false
                newNoteParentFolder = null
            },
            onConfirm = { inputName ->
                val targetPath = if (!parentPrefix.isNullOrEmpty()) {
                    "$parentPrefix/$inputName"
                } else {
                    inputName
                }
                showNewNoteDialog = false
                newNoteParentFolder = null
                val treeUri = resolveVaultTreeUri(context, allNotes)
                if (treeUri == null) {
                    onPickFolder()
                } else {
                    scope.launch {
                        val created = createNoteInVault(context, treeUri, allNotes, targetPath)
                        if (created != null) {
                            locallyAddedItems.add(created)
                            val parents = ancestorFolderPaths(created.relativePath)
                            if (parents.isNotEmpty()) {
                                expandedFolders = (expandedFolders + parents).distinct()
                            }
                            activeAction = NotesSidebarAction.FILES
                            onSelectNote(created)
                        }
                    }
                }
            }
        )
    }

    // ---- Create New Folder Dialog ----
    if (showNewFolderDialog) {
        val parentPrefix = newFolderParentFolder
        NameInputDialog(
            title = if (parentPrefix != null) "New Folder in $parentPrefix" else "New Folder",
            label = "Folder name",
            placeholder = "e.g. Projects",
            confirmText = "Create",
            onDismiss = {
                showNewFolderDialog = false
                newFolderParentFolder = null
            },
            onConfirm = { inputFolder ->
                val targetPath = if (!parentPrefix.isNullOrEmpty()) {
                    "$parentPrefix/$inputFolder"
                } else {
                    inputFolder
                }
                showNewFolderDialog = false
                newFolderParentFolder = null
                val treeUri = resolveVaultTreeUri(context, allNotes)
                if (treeUri == null) {
                    onPickFolder()
                } else {
                    scope.launch {
                        val createdDir = createFolderInVault(context, treeUri, allNotes, targetPath)
                        if (createdDir != null) {
                            locallyAddedItems.add(createdDir)
                            val toExpand = ancestorFolderPaths(createdDir.relativePath) + createdDir.relativePath
                            expandedFolders = (expandedFolders + toExpand).distinct()
                            activeAction = NotesSidebarAction.FILES
                        }
                    }
                }
            }
        )
    }

    // ---- Rename Note Dialog ----
    renamingNote?.let { targetNote ->
        NameInputDialog(
            title = "Rename Note",
            label = "New name",
            placeholder = "Note title",
            initialValue = targetNote.name.removeSuffix(".md"),
            confirmText = "Rename",
            onDismiss = { renamingNote = null },
            onConfirm = { newTitle ->
                renamingNote = null
                scope.launch {
                    val renamed = renameNoteInVault(context, targetNote, newTitle)
                    if (renamed != null) {
                        locallyRenamedMap[targetNote.relativePath] = renamed
                        val idx = locallyAddedItems.indexOfFirst { it.relativePath == targetNote.relativePath }
                        if (idx >= 0) locallyAddedItems[idx] = renamed
                        onNoteRenamed(targetNote, renamed)
                        if (targetNote.uri == selectedNoteUri) {
                            onSelectNote(renamed)
                        }
                    }
                }
            }
        )
    }

    // ---- Rename Folder Dialog ----
    renamingFolder?.let { targetFolder ->
        NameInputDialog(
            title = "Rename Folder",
            label = "New folder name",
            placeholder = "Folder name",
            initialValue = targetFolder.name,
            confirmText = "Rename",
            onDismiss = { renamingFolder = null },
            onConfirm = { newFolderName ->
                renamingFolder = null
                val treeUri = resolveVaultTreeUri(context, allNotes)
                if (treeUri != null) {
                    scope.launch {
                        val res = renameFolderInVault(context, treeUri, allNotes, targetFolder.path, newFolderName)
                        if (res != null) {
                            val (newFolderPath, newDirUri) = res
                            val oldPrefix = "${targetFolder.path}/"
                            val newPrefix = "$newFolderPath/"
                            val newDirFile = NoteFile(
                                uri = newDirUri,
                                name = newFolderName.trim().trim('/'),
                                relativePath = newFolderPath,
                                isDirectory = true
                            )
                            locallyRenamedMap[targetFolder.path] = newDirFile
                            locallyAddedItems.add(newDirFile)

                            allNotes.forEach { item ->
                                if (item.relativePath.startsWith(oldPrefix)) {
                                    val updatedRel = newPrefix + item.relativePath.substring(oldPrefix.length)
                                    locallyRenamedMap[item.relativePath] = item.copy(relativePath = updatedRel)
                                }
                            }
                            folderColors[targetFolder.path]?.let { savedColor ->
                                val updatedColors = folderColors.toMutableMap()
                                updatedColors.remove(targetFolder.path)
                                updatedColors[newFolderPath] = savedColor
                                folderColors = updatedColors
                                saveFolderColorsPref(prefs, updatedColors)
                            }
                            expandedFolders = expandedFolders.map { path ->
                                when {
                                    path == targetFolder.path -> newFolderPath
                                    path.startsWith(oldPrefix) -> newPrefix + path.substring(oldPrefix.length)
                                    else -> path
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // ---- Delete Note Confirmation Dialog ----
    deletingNote?.let { targetNote ->
        DeleteNoteDialog(
            noteName = targetNote.name.removeSuffix(".md"),
            onDismiss = { deletingNote = null },
            onConfirm = {
                deletingNote = null
                scope.launch {
                    if (deleteDocumentInVault(context, targetNote.uri)) {
                        locallyRemovedPaths.add(targetNote.relativePath)
                        onNoteDeleted(targetNote)
                    }
                }
            }
        )
    }

    // ---- Delete Folder Confirmation Dialog ----
    deletingFolder?.let { targetFolder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete folder?", fontWeight = FontWeight.SemiBold) },
            text = { Text("\"${targetFolder.name}\" and all of its contents will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingFolder = null
                        val treeUri = resolveVaultTreeUri(context, allNotes)
                        if (treeUri != null) {
                            scope.launch {
                                val folderItem = allNotes.firstOrNull {
                                    it.isDirectory && it.relativePath.equals(targetFolder.path, ignoreCase = true)
                                }
                                val dirUri = folderItem?.uri ?: ensureSubfolderDocumentUri(
                                    context.contentResolver,
                                    treeUri,
                                    allNotes,
                                    targetFolder.path.split('/').filter { it.isNotEmpty() }
                                )
                                if (dirUri != null && deleteDocumentInVault(context, dirUri)) {
                                    locallyRemovedPaths.add(targetFolder.path)
                                    expandedFolders = expandedFolders.filterNot {
                                        it == targetFolder.path || it.startsWith("${targetFolder.path}/")
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text("Cancel")
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
    val visibleEntries = remember(treeRoot, expandedSet, pinnedPaths) {
        flattenSidebarTree(treeRoot, expandedSet, pinnedPaths)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ---- Top Glassy Action Bar ----
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
                    newNoteParentFolder = null
                    showNewNoteDialog = true
                }
            },
            onNewFolderClick = {
                showPlusMenu = false
                if (!hasFolder) {
                    onPickFolder()
                } else {
                    newFolderParentFolder = null
                    showNewFolderDialog = true
                }
            }
        )

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
                    val density = LocalDensity.current
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(visibleEntries, key = { it.key }) { entry ->
                            when (entry) {
                                is SidebarTreeEntry.FolderEntry -> {
                                    val isMenuOpen = contextMenuFolder?.path == entry.path
                                    val chevronRotation by animateFloatAsState(
                                        targetValue = if (entry.isExpanded) 0f else -90f,
                                        label = "folderChevron"
                                    )
                                    val customColorArgb = folderColors[entry.path]
                                    val folderTint = if (customColorArgb != null) {
                                        Color(customColorArgb)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    }
                                    val rowShape = RoundedCornerShape(6.dp)

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(rowShape)
                                                .then(
                                                    if (isMenuOpen) {
                                                        Modifier.border(
                                                            width = 1.dp,
                                                            color = MaterialTheme.colorScheme.outlineVariant,
                                                            shape = rowShape
                                                        )
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .combinedClickable(
                                                    onClick = {
                                                        expandedFolders = if (entry.isExpanded) {
                                                            expandedFolders - entry.path
                                                        } else {
                                                            expandedFolders + entry.path
                                                        }
                                                    },
                                                    onLongClick = {
                                                        contextMenuNote = null
                                                        contextMenuFolder = entry
                                                    }
                                                )
                                                .padding(
                                                    start = (8 + entry.depth * 14).dp,
                                                    end = 10.dp,
                                                    top = 7.dp,
                                                    bottom = 7.dp
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
                                                tint = folderTint,
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

                                        if (isMenuOpen) {
                                            val popupOffset = with(density) {
                                                IntOffset(
                                                    x = (72 + (entry.depth * 10).coerceAtMost(40)).dp.roundToPx(),
                                                    y = 34.dp.roundToPx()
                                                )
                                            }
                                            FolderHoverPopupMenu(
                                                offset = popupOffset,
                                                currentFolderColorArgb = customColorArgb,
                                                onDismiss = { contextMenuFolder = null },
                                                onNewNote = {
                                                    contextMenuFolder = null
                                                    newNoteParentFolder = entry.path
                                                    showNewNoteDialog = true
                                                },
                                                onNewFolder = {
                                                    contextMenuFolder = null
                                                    newFolderParentFolder = entry.path
                                                    showNewFolderDialog = true
                                                },
                                                onSelectColor = { chosenColor ->
                                                    val updated = folderColors.toMutableMap()
                                                    if (chosenColor == null) {
                                                        updated.remove(entry.path)
                                                    } else {
                                                        updated[entry.path] = chosenColor.toArgb()
                                                    }
                                                    folderColors = updated
                                                    saveFolderColorsPref(prefs, updated)
                                                    contextMenuFolder = null
                                                },
                                                onMakeCopy = {
                                                    contextMenuFolder = null
                                                    val treeUri = resolveVaultTreeUri(context, allNotes)
                                                    if (treeUri != null) {
                                                        scope.launch {
                                                            val createdItems = copyFolderInVault(context, treeUri, allNotes, entry.path)
                                                            if (createdItems.isNotEmpty()) {
                                                                locallyAddedItems.addAll(createdItems)
                                                                val rootCopy = createdItems.first().relativePath
                                                                expandedFolders = (expandedFolders + rootCopy).distinct()
                                                            }
                                                        }
                                                    }
                                                },
                                                onRename = {
                                                    contextMenuFolder = null
                                                    renamingFolder = entry
                                                },
                                                onDelete = {
                                                    contextMenuFolder = null
                                                    deletingFolder = entry
                                                }
                                            )
                                        }
                                    }
                                }

                                is SidebarTreeEntry.NoteEntry -> {
                                    val note = entry.note
                                    val isMenuOpen = contextMenuNote?.uri == note.uri
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
                                    val rowShape = RoundedCornerShape(6.dp)

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(rowShape)
                                                .background(containerColor)
                                                .then(
                                                    if (isMenuOpen) {
                                                        Modifier.border(
                                                            width = 1.dp,
                                                            color = MaterialTheme.colorScheme.outlineVariant,
                                                            shape = rowShape
                                                        )
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .combinedClickable(
                                                    onClick = { onSelectNote(note) },
                                                    onLongClick = {
                                                        contextMenuFolder = null
                                                        contextMenuNote = note
                                                    }
                                                )
                                                .padding(
                                                    start = (12 + entry.depth * 14).dp,
                                                    end = 12.dp,
                                                    top = 8.dp,
                                                    bottom = 8.dp
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
                                            if (entry.isPinned) {
                                                Spacer(Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = PinMenuIcon,
                                                    contentDescription = "Pinned",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        if (isMenuOpen) {
                                            val popupOffset = with(density) {
                                                IntOffset(
                                                    x = (72 + (entry.depth * 10).coerceAtMost(40)).dp.roundToPx(),
                                                    y = 34.dp.roundToPx()
                                                )
                                            }
                                            FileHoverPopupMenu(
                                                offset = popupOffset,
                                                isPinned = entry.isPinned,
                                                onDismiss = { contextMenuNote = null },
                                                onOpenInNewTab = {
                                                    contextMenuNote = null
                                                    addUriToSavedOpenTabs(context, note.uri)
                                                    onSelectNote(note)
                                                },
                                                onMakeCopy = {
                                                    contextMenuNote = null
                                                    val treeUri = resolveVaultTreeUri(context, allNotes)
                                                    if (treeUri != null) {
                                                        scope.launch {
                                                            val copy = copyNoteInVault(context, treeUri, allNotes, note)
                                                            if (copy != null) {
                                                                locallyAddedItems.add(copy)
                                                            }
                                                        }
                                                    }
                                                },
                                                onTogglePin = {
                                                    contextMenuNote = null
                                                    onTogglePin(note)
                                                },
                                                onRename = {
                                                    contextMenuNote = null
                                                    renamingNote = note
                                                },
                                                onHide = {
                                                    contextMenuNote = null
                                                    val updated = hiddenPaths + note.relativePath
                                                    hiddenPaths = updated
                                                    saveStringSetPref(prefs, PREF_HIDDEN_NOTES, updated)
                                                },
                                                onDelete = {
                                                    contextMenuNote = null
                                                    deletingNote = note
                                                }
                                            )
                                        }
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
                        }                        }
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