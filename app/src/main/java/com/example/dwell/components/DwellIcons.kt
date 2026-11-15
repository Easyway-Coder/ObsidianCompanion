package com.example.dwell.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

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

internal object CalloutIcons {
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
// Callout styling helpers (13 official Obsidian types + aliases)
// ============================================================================

internal fun calloutAccentColor(type: String): Color = when (type.lowercase()) {
    "note", "info", "todo" -> Color(0xFF448AFF)
    "abstract", "summary", "tldr", "tip", "hint", "important" -> Color(0xFF53DFDD)
    "success", "check", "done" -> Color(0xFF44CF6E)
    "question", "help", "faq", "warning", "caution", "attention" -> Color(0xFFE9973F)
    "failure", "fail", "missing", "danger", "error", "bug" -> Color(0xFFFB464C)
    "example" -> Color(0xFFA882FF)
    "quote", "cite" -> Color(0xFF9E9E9E)
    else -> Color(0xFF448AFF) // Unrecognized types default to Note style in Obsidian
}

internal fun calloutIcon(type: String): ImageVector = when (type.lowercase()) {
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
// Custom Stroke Icons for Task Swipe Actions
// ============================================================================

internal val TimerActionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TimerAction",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Circular arc with top-left arrow head
            moveTo(3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(8.4f, 20.5f, 5.3f, 18.2f, 4.1f, 15f)
            // Arrow head near top-left
            moveTo(3.2f, 7.5f)
            lineTo(3.5f, 12f)
            lineTo(7.8f, 10.8f)
            // Clock hands
            moveTo(12f, 7.5f)
            lineTo(12f, 12f)
            lineTo(15.2f, 13.8f)
        }
    }.build()
}

internal val DeleteActionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DeleteAction",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Top lid bar
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            // Top handle
            moveTo(9f, 6f)
            lineTo(9f, 4f)
            quadTo(9f, 3f, 10f, 3f)
            lineTo(14f, 3f)
            quadTo(15f, 3f, 15f, 4f)
            lineTo(15f, 6f)
            // Bin body
            moveTo(6f, 6f)
            lineTo(6.8f, 19.2f)
            quadTo(6.9f, 21f, 8.7f, 21f)
            lineTo(15.3f, 21f)
            quadTo(17.1f, 21f, 17.2f, 19.2f)
            lineTo(18f, 6f)
            // Inner vertical lines
            moveTo(10f, 10f)
            lineTo(10f, 17f)
            moveTo(14f, 10f)
            lineTo(14f, 17f)
        }
    }.build()
}

// ============================================================================
// Sidebar Glassy Bar & Vault Switcher Icons (matching Image 1 & Image 2)
// ============================================================================

internal val ChevronsUpDownIcon: ImageVector by lazy {
    buildStrokeIcon("ChevronsUpDown", strokeWidth = 2.0f) {
        // Top chevron (^)
        moveTo(7f, 9f)
        lineTo(12f, 4f)
        lineTo(17f, 9f)
        // Bottom chevron (v)
        moveTo(7f, 15f)
        lineTo(12f, 20f)
        lineTo(17f, 15f)
    }
}

internal val GlassFolderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GlassFolder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Top tab outline + solid rounded folder body matching Image 1
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 8.5f)
            lineTo(3f, 6.5f)
            quadTo(3f, 4.5f, 5f, 4.5f)
            lineTo(9.5f, 4.5f)
            quadTo(10.8f, 4.5f, 11.5f, 5.6f)
            lineTo(12.4f, 7f)
            lineTo(19f, 7f)
            quadTo(21f, 7f, 21f, 9f)
        }
        path(
            fill = SolidColor(Color.Black)
        ) {
            moveTo(3f, 9.5f)
            quadTo(3f, 8f, 4.5f, 8f)
            lineTo(19.5f, 8f)
            quadTo(21f, 8f, 21f, 9.5f)
            lineTo(21f, 17.5f)
            quadTo(21f, 19.5f, 19f, 19.5f)
            lineTo(5f, 19.5f)
            quadTo(3f, 19.5f, 3f, 17.5f)
            close()
        }
    }.build()
}

internal val GlassPlusIcon: ImageVector by lazy {
    buildStrokeIcon("GlassPlus", strokeWidth = 1.9f) {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }
}

internal val GlassSearchIcon: ImageVector by lazy {
    buildStrokeIcon("GlassSearch", strokeWidth = 1.9f) {
        drawCirclePath(11f, 11f, 6.2f)
        moveTo(15.6f, 15.6f)
        lineTo(20f, 20f)
    }
}

internal val GlassSettingsIcon: ImageVector by lazy {
    buildStrokeIcon("GlassSettings", strokeWidth = 1.85f) {
        // Center circle
        drawCirclePath(12f, 12f, 3.1f)
        // Outer 8-tooth gear contour
        moveTo(10.8f, 2.5f)
        lineTo(13.2f, 2.5f)
        lineTo(13.7f, 4.6f)
        lineTo(15.3f, 5.3f)
        lineTo(17.2f, 4.1f)
        lineTo(18.9f, 5.8f)
        lineTo(17.7f, 7.7f)
        lineTo(18.4f, 9.3f)
        lineTo(20.5f, 9.8f)
        lineTo(20.5f, 12.2f)
        lineTo(18.4f, 12.7f)
        lineTo(17.7f, 14.3f)
        lineTo(18.9f, 16.2f)
        lineTo(17.2f, 17.9f)
        lineTo(15.3f, 16.7f)
        lineTo(13.7f, 17.4f)
        lineTo(13.2f, 19.5f)
        lineTo(10.8f, 19.5f)
        lineTo(10.3f, 17.4f)
        lineTo(8.7f, 16.7f)
        lineTo(6.8f, 17.9f)
        lineTo(5.1f, 16.2f)
        lineTo(6.3f, 14.3f)
        lineTo(5.6f, 12.7f)
        lineTo(3.5f, 12.2f)
        lineTo(3.5f, 9.8f)
        lineTo(5.6f, 9.3f)
        lineTo(6.3f, 7.7f)
        lineTo(5.1f, 5.8f)
        lineTo(6.8f, 4.1f)
        lineTo(8.7f, 5.3f)
        lineTo(10.3f, 4.6f)
        close()
    }
}
