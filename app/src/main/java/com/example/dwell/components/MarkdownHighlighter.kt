package com.example.dwell.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// LIVE EDITOR: styling is applied as a VisualTransformation, so the text the
// user types is untouched (same length, same cursor) but is drawn formatted.
// Markers like ** # - [ ] == %% $ $$ stay visible, just dimmed.
// ============================================================================

internal data class HighlightColors(
    val accent: Color,      // list markers, links, wikilinks, tags, callouts, math
    val muted: Color,       // syntax characters (**, #, `, $, ==, %%, ...)
    val soft: Color,        // quote / completed-task text
    val codeBg: Color,
    val highlightBg: Color  // ==highlight== background
)

internal class MarkdownHighlighter(private val colors: HighlightColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightMarkdown(text.text, colors), OffsetMapping.Identity)
}

@Composable
internal fun rememberMarkdownHighlighter(): MarkdownHighlighter {
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
