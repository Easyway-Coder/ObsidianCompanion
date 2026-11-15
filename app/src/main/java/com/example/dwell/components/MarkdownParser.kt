package com.example.dwell.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// ============================================================================
// Obsidian Flavored Markdown (OFM) regexes
// ============================================================================

// "- [ ] Buy milk" / "- [x] Done" / "- [/] In progress" / "- [-] Cancelled"
// g1 = indent, g2 = status char, g3 = text
internal val TASK_LINE_RE = Regex("""^(\s*)[-*+]\s+\[([ xX/\->!?])]\s+(.*)""")

// Same thing but only the prefix (used by the live-editor highlighter)
internal val TASK_PREFIX_RE = Regex("""^(\s*)([-*+])\s+(\[[ xX/\->!?]])\s+""")
internal val HEADING_RE = Regex("""^(\s*)(#{1,6})\s+""")
internal val CALLOUT_HEADER_RE = Regex("""^(\s*)>\s*\[!([\w-]+)]([+-])?(?:\s+(.*))?$""")
internal val QUOTE_RE = Regex("""^(\s*)>\s?""")
internal val BULLET_RE = Regex("""^(\s*)[-*+]\s+""")
internal val ORDERED_RE = Regex("""^(\s*)\d+[.)]\s+""")
internal val RULE_RE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
internal val FRONTMATTER_KEY_RE = Regex("""^([\w-]+)\s*:\s*(.*)$""")
internal val FOOTNOTE_DEF_RE = Regex("""^\[\^([^\]]+)]:\s+(.*)$""")
internal val TABLE_ROW_RE = Regex("""^\|.*\|$""")
internal val TABLE_SEP_RE = Regex("""^\|(?:\s*:?-{3,}:?\s*\|)+$""")
internal val BLOCK_ID_END_RE = Regex("""(\s+\^[A-Za-z0-9-]+)\s*$""")

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
internal val INLINE_RE = Regex(
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

internal val INLINE_COMMENT_STRIP_RE = Regex("""%%.*?%%""")

// ============================================================================
// Math detection (shared by the editor highlighter and the preview)
// ============================================================================

internal class MathSpan(val range: IntRange, val latex: String, val display: Boolean) {
    val delim: Int get() = if (display) 2 else 1
}

private const val MASK_CHAR = '\uE000'

// Finds $inline$ and $$display$$ math in a piece of text. Skips `code` and \$ escapes.
// Inline rules (same as Obsidian): no space right after the opening $ or right before
// the closing $, and the closing $ isn't followed by a digit ("costs $5 and $10" is not math).
internal fun findMathSpans(s: String): List<MathSpan> {
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
internal fun maskSpans(s: String, spans: List<MathSpan>): String {
    if (spans.isEmpty()) return s
    val chars = s.toCharArray()
    for (sp in spans) for (k in sp.range) chars[k] = MASK_CHAR
    return String(chars)
}

// "$$a$$$$b$$" -> ["a", "b"]; null when the text isn't made purely of $$...$$ groups.
// The quadruple dollar ($$$$) is the break between two equations.
internal fun parseDisplayMathParts(text: String): List<String>? {
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
internal fun formatWikilinkDisplay(raw: String): String {
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

internal fun stripInlineCommentsAndBlockId(s: String): String =
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
