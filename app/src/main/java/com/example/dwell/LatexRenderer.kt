package com.example.dwell

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max
import kotlin.math.min
import android.graphics.Canvas as NativeCanvas

// ============================================================================
// A small TeX-style math engine.
//
//   LaTeX string --LatexParser--> MNode tree --layoutNode--> Lay (sized, drawable)
//
// Glyphs come from the KaTeX fonts (res/font/katex_*.ttf), positions follow
// TeX's rules (axis height, fraction bars, script shifts, math spacing), and
// everything is drawn straight onto a canvas. So fractions, roots, \boxed,
// matrices and big operators are real shapes, not text tricks.
// ============================================================================

internal enum class Face { MAIN, BOLD, TEXT_ITALIC, MATH, AMS, CAL, SIZE1, SIZE2 }

internal class MathFonts(private val context: Context) {
    private fun load(id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.SERIF

    private val faces: Map<Face, Typeface> = mapOf(
        Face.MAIN to load(R.font.katex_main),
        Face.BOLD to load(R.font.katex_main_bold),
        Face.TEXT_ITALIC to load(R.font.katex_main_italic),
        Face.MATH to load(R.font.katex_math_italic),
        Face.AMS to load(R.font.katex_ams),
        Face.CAL to load(R.font.katex_caligraphic),
        Face.SIZE1 to load(R.font.katex_size1),
        Face.SIZE2 to load(R.font.katex_size2)
    )

    fun typeface(face: Face): Typeface = faces.getValue(face)

    // Shared scratch objects (everything is measured/drawn on the main thread)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = Rect()
}

@Composable
internal fun rememberMathFonts(): MathFonts {
    val ctx = LocalContext.current
    return remember(ctx) { MathFonts(ctx) }
}

// ----------------------------------------------------------------------------
// Syntax tree
// ----------------------------------------------------------------------------

internal enum class Kind { ORD, OP, BIN, REL, OPEN, CLOSE, PUNCT, NONE }

internal sealed class MNode {
    class Sym(
        val text: String,
        val kind: Kind = Kind.ORD,
        val face: Face = Face.MAIN,
        val alnum: Boolean = false
    ) : MNode()

    class Row(val items: List<MNode>) : MNode()
    class Script(val base: MNode, val sup: MNode?, val sub: MNode?, val limits: Boolean?) : MNode()
    class Frac(val num: MNode, val den: MNode, val bar: Boolean = true, val forceDisplay: Boolean? = null) : MNode()
    class Sqrt(val index: MNode?, val body: MNode) : MNode()
    class Boxed(val body: MNode) : MNode()
    class Delim(val left: String, val body: MNode, val right: String, val minEm: Float = 0f) : MNode()
    class Table(val rows: List<List<MNode>>, val aligns: String, val display: Boolean, val aligned: Boolean) : MNode()
    class Accent(val body: MNode, val kind: String) : MNode()
    class Styled(val body: MNode, val face: Face) : MNode()
    class TextRun(val text: String, val face: Face) : MNode()
    class Space(val em: Float) : MNode()
    class Op(val text: String, val big: Boolean, val limits: Boolean) : MNode()
    object Break : MNode()
}

private val EMPTY_NODE = MNode.Row(emptyList())

// ----------------------------------------------------------------------------
// Symbol tables
// ----------------------------------------------------------------------------

private val GREEK_LOWER = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ",
    "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
    "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
    "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ",
    "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ", "phi" to "ϕ", "varphi" to "φ",
    "chi" to "χ", "psi" to "ψ", "omega" to "ω"
)

private val GREEK_UPPER = mapOf(
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
    "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω"
)

private val BIN_SYMS = mapOf(
    "times" to "×", "div" to "÷", "cdot" to "⋅", "pm" to "±", "mp" to "∓", "ast" to "∗",
    "star" to "⋆", "circ" to "∘", "bullet" to "∙", "oplus" to "⊕", "ominus" to "⊖",
    "otimes" to "⊗", "oslash" to "⊘", "odot" to "⊙", "cup" to "∪", "cap" to "∩",
    "setminus" to "∖", "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨"
)

private val REL_SYMS = mapOf(
    "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
    "approx" to "≈", "equiv" to "≡", "cong" to "≅", "sim" to "∼", "simeq" to "≃",
    "propto" to "∝", "ll" to "≪", "gg" to "≫", "prec" to "≺", "succ" to "≻", "perp" to "⊥",
    "parallel" to "∥", "mid" to "∣", "nmid" to "∤", "in" to "∈", "notin" to "∉", "ni" to "∋",
    "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
    "to" to "→", "rightarrow" to "→", "gets" to "←", "leftarrow" to "←",
    "leftrightarrow" to "↔", "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
    "iff" to "⟺", "implies" to "⟹", "impliedby" to "⟸", "mapsto" to "↦",
    "longrightarrow" to "⟶", "longleftarrow" to "⟵", "uparrow" to "↑", "downarrow" to "↓"
)

private val ORD_SYMS = mapOf(
    "infty" to "∞", "partial" to "∂", "nabla" to "∇", "aleph" to "ℵ", "hbar" to "ℏ",
    "ell" to "ℓ", "wp" to "℘", "Re" to "ℜ", "Im" to "ℑ", "emptyset" to "∅", "varnothing" to "∅",
    "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬", "lnot" to "¬",
    "prime" to "′", "degree" to "°", "angle" to "∠", "triangle" to "△",
    "dots" to "…", "ldots" to "…", "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱",
    "therefore" to "∴", "because" to "∵", "vert" to "|", "Vert" to "∥", "square" to "□"
)

private val OPEN_SYMS = mapOf(
    "langle" to "⟨", "lceil" to "⌈", "lfloor" to "⌊", "lbrace" to "{", "lbrack" to "[",
    "lvert" to "|", "lVert" to "∥"
)
private val CLOSE_SYMS = mapOf(
    "rangle" to "⟩", "rceil" to "⌉", "rfloor" to "⌋", "rbrace" to "}", "rbrack" to "]",
    "rvert" to "|", "rVert" to "∥"
)

// name -> (glyph, drawn with limits above/below in display style)
private val BIG_OPS = mapOf(
    "sum" to ("∑" to true), "prod" to ("∏" to true), "coprod" to ("∐" to true),
    "bigcup" to ("⋃" to true), "bigcap" to ("⋂" to true),
    "int" to ("∫" to false), "oint" to ("∮" to false)
)

private val NAMED_OPS = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh",
    "tanh", "coth", "log", "ln", "lg", "exp", "lim", "limsup", "liminf", "sup", "inf",
    "max", "min", "det", "dim", "ker", "deg", "gcd", "arg", "mod", "Pr", "hom"
)
private val LIMIT_OPS = setOf("lim", "limsup", "liminf", "sup", "inf", "max", "min", "det", "gcd", "Pr")

private val CLOSERS = setOf(")", "]", "}", "⟩", "⌉", "⌋")

// ----------------------------------------------------------------------------
// Parser
// ----------------------------------------------------------------------------

internal class LatexParser(private val s: String) {
    private var p = 0

    fun parse(): MNode = parseRow(emptySet())

    private fun skipWs() {
        while (p < s.length && s[p].isWhitespace()) p++
    }

    private fun startsWithCmd(name: String): Boolean {
        if (!s.startsWith("\\$name", p)) return false
        val after = p + 1 + name.length
        return after >= s.length || !s[after].isLetter()
    }

    // stops: "}" "&" "\\\\" "\\right" "\\end"
    private fun parseRow(stops: Set<String>): MNode.Row {
        val items = mutableListOf<MNode>()
        while (p < s.length) {
            val c = s[p]
            if (c.isWhitespace()) { p++; continue }
            if (c == '}') {
                if ("}" in stops) break
                p++; continue
            }
            if (c == '&') {
                if ("&" in stops) break
                items.add(MNode.Space(0.5f)); p++; continue
            }
            if (c == '\\') {
                if (s.startsWith("\\\\", p)) {
                    if ("\\\\" in stops) break
                    p += 2
                    skipOptionalBracket()
                    items.add(MNode.Break)
                    continue
                }
                if ("\\right" in stops && startsWithCmd("right")) break
                if ("\\end" in stops && startsWithCmd("end")) break
            }
            items.add(parseScripts(parseAtom(false)))
        }
        return MNode.Row(items)
    }

    private fun skipOptionalBracket() {
        // "\\[2pt]" spacing after a line break
        if (p < s.length && s[p] == '[') {
            val close = s.indexOf(']', p)
            if (close != -1) p = close + 1
        }
    }

    private fun parseScripts(base: MNode): MNode {
        var sup: MNode? = null
        var sub: MNode? = null
        var limits: Boolean? = null
        while (true) {
            skipWs()
            if (p >= s.length) break
            if (s.startsWith("\\limits", p)) { limits = true; p += 7; continue }
            if (s.startsWith("\\nolimits", p)) { limits = false; p += 9; continue }
            when (s[p]) {
                '^' -> {
                    p++
                    val a = parseScriptArg()
                    sup = if (sup == null) a else MNode.Row(listOf(sup, a))
                }
                '_' -> { p++; sub = parseScriptArg() }
                '\'' -> {
                    p++
                    val pr = MNode.Sym("′")
                    sup = if (sup == null) pr else MNode.Row(listOf(sup, pr))
                }
                else -> break
            }
        }
        return if (sup == null && sub == null) base else MNode.Script(base, sup, sub, limits)
    }

    private fun parseScriptArg(): MNode {
        skipWs()
        if (p >= s.length) return EMPTY_NODE
        return parseAtom(true)
    }

    private fun parseAtom(single: Boolean): MNode {
        val c = s[p]
        when {
            c == '{' -> {
                p++
                val r = parseRow(setOf("}"))
                if (p < s.length && s[p] == '}') p++
                return r
            }
            c == '\\' -> return parseCommand()
            c.isLetter() -> {
                p++
                return MNode.Sym(c.toString(), Kind.ORD, if (c.code < 128) Face.MATH else Face.MAIN, true)
            }
            c.isDigit() -> {
                var q = p + 1
                if (!single) {
                    while (q < s.length && (s[q].isDigit() || (s[q] == '.' && q + 1 < s.length && s[q + 1].isDigit()))) q++
                }
                val t = s.substring(p, q)
                p = q
                return MNode.Sym(t, Kind.ORD, Face.MAIN, true)
            }
            c == '~' -> { p++; return MNode.Space(0.25f) }
            else -> { p++; return charSym(c) }
        }
    }

    private fun charSym(c: Char): MNode = when (c) {
        '+' -> MNode.Sym("+", Kind.BIN)
        '-' -> MNode.Sym("−", Kind.BIN)
        '*' -> MNode.Sym("∗", Kind.BIN)
        '=', '<', '>', ':' -> MNode.Sym(c.toString(), Kind.REL)
        ',', ';' -> MNode.Sym(c.toString(), Kind.PUNCT)
        '(', '[' -> MNode.Sym(c.toString(), Kind.OPEN)
        ')', ']' -> MNode.Sym(c.toString(), Kind.CLOSE)
        else -> MNode.Sym(c.toString(), Kind.ORD)
    }

    private fun parseCommand(): MNode {
        p++ // backslash
        if (p >= s.length) return EMPTY_NODE
        val first = s[p]
        if (!first.isLetter()) {
            p++
            return when (first) {
                ',' -> MNode.Space(0.167f)
                ':', '>' -> MNode.Space(0.222f)
                ';' -> MNode.Space(0.278f)
                '!' -> MNode.Space(-0.167f)
                ' ' -> MNode.Space(0.25f)
                '{' -> MNode.Sym("{", Kind.OPEN)
                '}' -> MNode.Sym("}", Kind.CLOSE)
                '|' -> MNode.Sym("∥")
                else -> MNode.Sym(first.toString())
            }
        }
        var e = p
        while (e < s.length && s[e].isLetter()) e++
        val name = s.substring(p, e)
        p = e
        return command(name)
    }

    private fun arg(): MNode {
        skipWs()
        if (p >= s.length) return EMPTY_NODE
        return parseAtom(true)
    }

    // Raw text of the next {...} group (or one character)
    private fun rawArg(): String {
        skipWs()
        if (p >= s.length) return ""
        if (s[p] != '{') return s[p++].toString()
        var depth = 0
        val start = p + 1
        while (p < s.length) {
            when (s[p]) {
                '\\' -> p++
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val out = s.substring(start, p)
                        p++
                        return out
                    }
                }
            }
            p++
        }
        return s.substring(start.coerceAtMost(s.length))
    }

    private fun readDelim(): String {
        skipWs()
        if (p >= s.length) return ""
        val c = s[p]
        if (c == '.') { p++; return "" }
        if (c == '\\') {
            p++
            if (p >= s.length) return ""
            if (!s[p].isLetter()) {
                val d = s[p++]
                return when (d) { '{' -> "{"; '}' -> "}"; '|' -> "∥"; else -> "" }
            }
            var e = p
            while (e < s.length && s[e].isLetter()) e++
            val name = s.substring(p, e)
            p = e
            return OPEN_SYMS[name] ?: CLOSE_SYMS[name] ?: ORD_SYMS[name] ?: ""
        }
        p++
        return if (c == '|') "|" else c.toString()
    }

    private fun command(name: String): MNode {
        when (name) {
            "frac", "dfrac", "tfrac" -> {
                val a = arg(); val b = arg()
                return MNode.Frac(a, b, true, when (name) { "dfrac" -> true; "tfrac" -> false; else -> null })
            }
            "binom" -> {
                val a = arg(); val b = arg()
                return MNode.Delim("(", MNode.Frac(a, b, bar = false), ")")
            }
            "sqrt" -> {
                skipWs()
                var idx: MNode? = null
                if (p < s.length && s[p] == '[') {
                    val close = s.indexOf(']', p)
                    if (close != -1) {
                        idx = LatexParser(s.substring(p + 1, close)).parse()
                        p = close + 1
                    }
                }
                return MNode.Sqrt(idx, arg())
            }
            "boxed", "fbox" -> return MNode.Boxed(arg())
            "text", "textrm", "mbox", "textnormal" -> return MNode.TextRun(rawArg(), Face.MAIN)
            "textbf" -> return MNode.TextRun(rawArg(), Face.BOLD)
            "textit" -> return MNode.TextRun(rawArg(), Face.TEXT_ITALIC)
            "operatorname" -> {
                if (p < s.length && s[p] == '*') p++
                return MNode.Op(rawArg(), big = false, limits = false)
            }
            "mathrm", "mathsf", "mathtt", "mathfrak" -> return MNode.Styled(arg(), Face.MAIN)
            "mathbf", "boldsymbol", "bm", "pmb" -> return MNode.Styled(arg(), Face.BOLD)
            "mathit" -> return MNode.Styled(arg(), Face.MATH)
            "mathbb" -> return MNode.Styled(arg(), Face.AMS)
            "mathcal", "mathscr" -> return MNode.Styled(arg(), Face.CAL)
            "hat", "widehat", "tilde", "widetilde", "bar", "overline", "vec", "overrightarrow",
            "dot", "ddot", "underline" -> {
                val kind = when (name) {
                    "widehat" -> "hat"; "widetilde" -> "tilde"; "overrightarrow" -> "vec"; else -> name
                }
                return MNode.Accent(arg(), kind)
            }
            "left" -> {
                val l = readDelim()
                val body = parseRow(setOf("\\right"))
                var r = ""
                if (startsWithCmd("right")) {
                    p += 6
                    r = readDelim()
                }
                return MNode.Delim(l, body, r)
            }
            "right" -> return MNode.Sym(readDelim())
            "big", "bigl", "bigr" -> return sizedDelim(1.2f)
            "Big", "Bigl", "Bigr" -> return sizedDelim(1.8f)
            "bigg", "biggl", "biggr" -> return sizedDelim(2.4f)
            "Bigg", "Biggl", "Biggr" -> return sizedDelim(3.0f)
            "begin" -> return parseEnv()
            "end" -> { rawArg(); return EMPTY_NODE }
            "quad" -> return MNode.Space(1f)
            "qquad" -> return MNode.Space(2f)
            "hspace", "vspace" -> { rawArg(); return EMPTY_NODE }
            "color" -> { rawArg(); return EMPTY_NODE }
            "textcolor" -> { rawArg(); return arg() }
            "cancel", "displaystyle", "textstyle", "scriptstyle", "limits", "nolimits",
            "not", "mathstrut", "strut" -> return EMPTY_NODE
            "phantom", "hphantom", "vphantom" -> { arg(); return EMPTY_NODE }
            "pmod" -> return MNode.Row(
                listOf(
                    MNode.Space(0.444f), MNode.Sym("(", Kind.OPEN), MNode.Op("mod", false, false),
                    MNode.Space(0.333f), arg(), MNode.Sym(")", Kind.CLOSE)
                )
            )
        }
        GREEK_LOWER[name]?.let { return MNode.Sym(it, Kind.ORD, Face.MATH, true) }
        GREEK_UPPER[name]?.let { return MNode.Sym(it, Kind.ORD, Face.MAIN, true) }
        BIN_SYMS[name]?.let { return MNode.Sym(it, Kind.BIN) }
        REL_SYMS[name]?.let { return MNode.Sym(it, Kind.REL) }
        ORD_SYMS[name]?.let { return MNode.Sym(it, Kind.ORD) }
        OPEN_SYMS[name]?.let { return MNode.Sym(it, Kind.OPEN) }
        CLOSE_SYMS[name]?.let { return MNode.Sym(it, Kind.CLOSE) }
        BIG_OPS[name]?.let { return MNode.Op(it.first, big = true, limits = it.second) }
        if (name in NAMED_OPS) return MNode.Op(name, big = false, limits = name in LIMIT_OPS)
        // Unsupported command: show it literally so it's obvious what wasn't understood
        return MNode.TextRun("\\$name", Face.MAIN)
    }

    private fun sizedDelim(minEm: Float): MNode {
        val d = readDelim()
        return if (d in CLOSERS) MNode.Delim("", EMPTY_NODE, d, minEm)
        else MNode.Delim(d, EMPTY_NODE, "", minEm)
    }

    private fun parseEnv(): MNode {
        val env = rawArg().trim()
        var arraySpec = ""
        if (env == "array") arraySpec = rawArg().filter { it == 'l' || it == 'c' || it == 'r' }

        val rows = mutableListOf<List<MNode>>()
        var cur = mutableListOf<MNode>()
        while (true) {
            cur.add(parseRow(setOf("&", "\\\\", "\\end")))
            if (p >= s.length) break
            if (s[p] == '&') {
                p++
            } else if (s.startsWith("\\\\", p)) {
                p += 2
                skipOptionalBracket()
                rows.add(cur)
                cur = mutableListOf()
            } else { // \end{...}
                p += 4
                rawArg()
                break
            }
        }
        val onlyEmpty = cur.size == 1 && (cur[0] as? MNode.Row)?.items?.isEmpty() == true
        if (cur.isNotEmpty() && !onlyEmpty) rows.add(cur)

        val cols = rows.maxOfOrNull { it.size } ?: 0
        val aligned = env in setOf("aligned", "align", "align*", "split", "alignedat")
        val aligns = when {
            env == "array" -> arraySpec
            aligned -> "rl".repeat((cols + 1) / 2)
            env == "cases" -> "ll"
            env == "gathered" || env == "gather" || env == "gather*" -> "c"
            else -> "c".repeat(cols)
        }
        val table = MNode.Table(rows, aligns, display = aligned || env.startsWith("gather"), aligned = aligned)
        val (l, r) = when (env) {
            "pmatrix" -> "(" to ")"
            "bmatrix" -> "[" to "]"
            "Bmatrix" -> "{" to "}"
            "vmatrix" -> "|" to "|"
            "Vmatrix" -> "∥" to "∥"
            "cases" -> "{" to ""
            else -> "" to ""
        }
        return if (l.isEmpty() && r.isEmpty()) table else MNode.Delim(l, table, r)
    }
}

// ----------------------------------------------------------------------------
// Layout
// ----------------------------------------------------------------------------

// A laid-out piece of math: width / ascent / descent in px and a draw function.
// draw(canvas, left, baselineY)
internal class Lay(
    val w: Float,
    val asc: Float,
    val desc: Float,
    val draw: (NativeCanvas, Float, Float) -> Unit
)

private val NO_DRAW: (NativeCanvas, Float, Float) -> Unit = { _, _, _ -> }

internal class MathCtx(
    val fonts: MathFonts,
    val color: Int,
    val size: Float,
    val display: Boolean,
    val rootSize: Float = size,
    val override: Face? = null
) {
    fun sub(
        size: Float = this.size,
        display: Boolean = this.display,
        override: Face? = this.override
    ) = MathCtx(fonts, color, size, display, rootSize, override)

    fun script(): MathCtx = sub(size = max(size * 0.7f, rootSize * 0.5f), display = false)
    val tight: Boolean get() = size < rootSize * 0.99f
}

internal fun layoutLatex(latex: String, c: MathCtx): Lay =
    try {
        layoutNode(LatexParser(latex).parse(), c)
    } catch (e: Exception) {
        glyph(latex, Face.MAIN, c)
    }

private fun glyph(text: String, face: Face, c: MathCtx): Lay {
    val f = c.fonts
    val p = f.paint
    val tf = f.typeface(face)
    val size = c.size
    val color = c.color
    p.typeface = tf
    p.textSize = size
    var w = p.measureText(text)
    val blank = text.isBlank()
    p.getTextBounds(text, 0, text.length, f.rect)
    val asc = if (blank) 0f else max(0f, -f.rect.top.toFloat())
    val desc = if (blank) 0f else max(0f, f.rect.bottom.toFloat())
    if (face == Face.MATH && !blank) w = max(w, f.rect.right.toFloat()) // italic overhang
    return Lay(w, asc, desc) { cv, x, y ->
        p.typeface = tf
        p.textSize = size
        p.color = color
        p.style = Paint.Style.FILL
        cv.drawText(text, x, y, p)
    }
}

private fun kindOf(n: MNode): Kind = when (n) {
    is MNode.Sym -> n.kind
    is MNode.Op -> Kind.OP
    is MNode.Script -> kindOf(n.base)
    is MNode.Space -> Kind.NONE
    else -> Kind.ORD
}

private fun gapEm(a: Kind, b: Kind): Float = when {
    a == Kind.BIN || b == Kind.BIN -> 0.222f
    a == Kind.REL && b == Kind.REL -> 0f
    a == Kind.REL || b == Kind.REL -> 0.278f
    a == Kind.PUNCT -> 0.167f
    a == Kind.OP -> if (b == Kind.OPEN) 0f else 0.167f
    b == Kind.OP -> if (a == Kind.OPEN) 0f else 0.167f
    else -> 0f
}

internal fun layoutNode(n: MNode, c: MathCtx): Lay = when (n) {
    is MNode.Sym -> layoutSym(n, c)
    is MNode.Row -> layoutRow(n.items, c)
    is MNode.Script -> layoutScript(n, c)
    is MNode.Frac -> layoutFrac(n, c)
    is MNode.Sqrt -> layoutSqrt(n, c)
    is MNode.Boxed -> layoutBoxed(n, c)
    is MNode.Delim -> layoutDelim(n, c)
    is MNode.Table -> layoutTable(n, c)
    is MNode.Accent -> layoutAccent(n, c)
    is MNode.Styled -> layoutNode(n.body, c.sub(override = n.face))
    is MNode.TextRun -> glyph(n.text, n.face, c)
    is MNode.Space -> Lay(n.em * c.size, 0f, 0f, NO_DRAW)
    is MNode.Op -> layoutOp(n, c)
    MNode.Break -> Lay(0f, 0f, 0f, NO_DRAW)
}

private fun layoutSym(n: MNode.Sym, c: MathCtx): Lay {
    // Glyphs the KaTeX fonts lack are built from a base glyph + slash
    if (n.text == "≠" || n.text == "∉") {
        val base = glyph(if (n.text == "≠") "=" else "∈", Face.MAIN, c)
        val color = c.color
        val p = c.fonts.paint
        val size = c.size
        return Lay(base.w, max(base.asc, 0.7f * size), max(base.desc, 0.2f * size)) { cv, x, y ->
            base.draw(cv, x, y)
            p.color = color
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1f, 0.05f * size)
            cv.drawLine(x + base.w * 0.25f, y + 0.12f * size, x + base.w * 0.75f, y - 0.56f * size, p)
            p.style = Paint.Style.FILL
        }
    }
    val ov = c.override
    val face = if (n.alnum && ov != null) ov else n.face
    return glyph(n.text, face, c)
}

private fun layoutRow(items: List<MNode>, c: MathCtx): Lay {
    if (items.any { it is MNode.Break }) {
        val lines = mutableListOf<MutableList<MNode>>(mutableListOf())
        for (item in items) {
            if (item is MNode.Break) lines.add(mutableListOf()) else lines.last().add(item)
        }
        return stackCentered(lines.filter { it.isNotEmpty() }.map { layoutRow(it, c) }, c)
    }
    if (items.isEmpty()) return Lay(0f, 0f, 0f, NO_DRAW)

    // A binary operator with nothing sensible on its left/right is really an ordinary symbol (-x)
    val kinds = items.map { kindOf(it) }.toMutableList()
    var prev: Kind? = null
    for (i in items.indices) {
        if (kinds[i] == Kind.BIN) {
            val next = (i + 1 until items.size).map { kindOf(items[it]) }.firstOrNull { it != Kind.NONE }
            if (prev == null || prev in setOf(Kind.BIN, Kind.REL, Kind.OPEN, Kind.PUNCT, Kind.OP) ||
                next == null || next in setOf(Kind.REL, Kind.CLOSE, Kind.PUNCT)
            ) kinds[i] = Kind.ORD
        }
        if (kinds[i] != Kind.NONE) prev = kinds[i]
    }

    val lays = items.map { layoutNode(it, c) }
    val xs = FloatArray(lays.size)
    var x = 0f
    var asc = 0f
    var desc = 0f
    var prevKind: Kind? = null
    for (i in lays.indices) {
        val k = kinds[i]
        if (k != Kind.NONE && prevKind != null && !c.tight) x += gapEm(prevKind, k) * c.size
        xs[i] = x
        x += lays[i].w
        asc = max(asc, lays[i].asc)
        desc = max(desc, lays[i].desc)
        if (k != Kind.NONE) prevKind = k
    }
    return Lay(x, asc, desc) { cv, ox, y ->
        for (i in lays.indices) lays[i].draw(cv, ox + xs[i], y)
    }
}

private fun stackCentered(lines: List<Lay>, c: MathCtx): Lay {
    if (lines.isEmpty()) return Lay(0f, 0f, 0f, NO_DRAW)
    val w = lines.maxOf { it.w }
    val ys = FloatArray(lines.size)
    for (i in 1 until lines.size) {
        ys[i] = ys[i - 1] + max(1.2f * c.size, lines[i - 1].desc + lines[i].asc + 0.2f * c.size)
    }
    return Lay(w, lines[0].asc, ys.last() + lines.last().desc) { cv, x, y ->
        for (i in lines.indices) lines[i].draw(cv, x + (w - lines[i].w) / 2f, y + ys[i])
    }
}

private fun layoutOp(n: MNode.Op, c: MathCtx): Lay {
    if (!n.big) return glyph(n.text, Face.MAIN, c)
    val g = glyph(n.text, if (c.display) Face.SIZE2 else Face.SIZE1, c)
    // Centre the big glyph on the math axis, like TeX does
    val shift = (g.asc - g.desc) / 2f - 0.25f * c.size
    return Lay(g.w, g.asc - shift, g.desc + shift) { cv, x, y -> g.draw(cv, x, y + shift) }
}

private fun layoutScript(n: MNode.Script, c: MathCtx): Lay {
    val base = layoutNode(n.base, c)
    val sc = c.script()
    val sup = n.sup?.let { layoutNode(it, sc) }
    val sub = n.sub?.let { layoutNode(it, sc) }
    val op = n.base as? MNode.Op
    val stacked = op != null && (n.limits ?: (op.limits && c.display))

    if (stacked) {
        val w = max(base.w, max(sup?.w ?: 0f, sub?.w ?: 0f))
        val gap = 0.12f * c.size
        val asc = base.asc + (sup?.let { gap + it.asc + it.desc } ?: 0f)
        val desc = base.desc + (sub?.let { gap + it.asc + it.desc } ?: 0f)
        return Lay(w, asc, desc) { cv, x, y ->
            base.draw(cv, x + (w - base.w) / 2f, y)
            sup?.draw(cv, x + (w - sup.w) / 2f, y - base.asc - gap - sup.desc)
            sub?.draw(cv, x + (w - sub.w) / 2f, y + base.desc + gap + sub.asc)
        }
    }

    var u = 0f
    var d = 0f
    if (sup != null) {
        u = max(base.asc - 0.386f * sc.size, if (c.display) 0.36f * c.size else 0.30f * c.size)
        u = max(u, sup.desc + 0.11f * c.size)
    }
    if (sub != null) {
        d = max(base.desc + 0.05f * sc.size, 0.15f * c.size)
        d = max(d, sub.asc - 0.35f * c.size)
    }
    if (sup != null && sub != null) {
        val gap = (u - sup.desc) - (sub.asc - d)
        val need = 0.16f * c.size
        if (gap < need) d += need - gap
    }
    val w = base.w + max(sup?.w ?: 0f, sub?.w ?: 0f) + 0.03f * c.size
    val asc = max(base.asc, if (sup != null) u + sup.asc else 0f)
    val desc = max(base.desc, if (sub != null) d + sub.desc else 0f)
    return Lay(w, asc, desc) { cv, x, y ->
        base.draw(cv, x, y)
        sup?.draw(cv, x + base.w, y - u)
        sub?.draw(cv, x + base.w, y + d)
    }
}

private fun layoutFrac(n: MNode.Frac, c: MathCtx): Lay {
    val disp = n.forceDisplay ?: c.display
    val sz = if (disp) c.size else max(c.size * 0.7f, c.rootSize * 0.5f)
    val cc = c.sub(size = sz, display = false)
    val num = layoutNode(n.num, cc)
    val den = layoutNode(n.den, cc)
    val axis = 0.25f * c.size
    val rule = if (n.bar) max(1f, 0.04f * c.size) else 0f
    var u = (if (disp) 0.677f else 0.394f) * c.size
    var v = (if (disp) 0.686f else 0.345f) * c.size
    val clr = (if (disp) 3f else 1f) * max(rule, 0.04f * c.size)
    u = max(u, clr + rule / 2f + axis + num.desc)
    v = max(v, clr + rule / 2f - axis + den.asc)
    val pad = 0.1f * c.size
    val w = max(num.w, den.w) + 2f * pad
    val p = c.fonts.paint
    val color = c.color
    return Lay(w, u + num.asc, v + den.desc) { cv, x, y ->
        num.draw(cv, x + (w - num.w) / 2f, y - u)
        den.draw(cv, x + (w - den.w) / 2f, y + v)
        if (n.bar) {
            p.color = color
            p.style = Paint.Style.FILL
            cv.drawRect(x + pad, y - axis - rule / 2f, x + w - pad, y - axis + rule / 2f, p)
        }
    }
}

private fun layoutSqrt(n: MNode.Sqrt, c: MathCtx): Lay {
    val body = layoutNode(n.body, c)
    val rule = max(1f, 0.04f * c.size)
    val clr = (if (c.display) 0.10f else 0.07f) * c.size
    val top = max(body.asc, 0.45f * c.size) + clr + rule
    val bottom = body.desc
    val radW = 0.6f * c.size
    val idx = n.index?.let { layoutNode(it, c.sub(size = max(c.size * 0.5f, c.rootSize * 0.5f), display = false)) }
    val lead = if (idx != null) max(0f, idx.w - 0.45f * radW) else 0f
    val tail = 0.04f * c.size
    val w = lead + radW + body.w + tail
    val p = c.fonts.paint
    val color = c.color
    return Lay(w, top, bottom) { cv, x, y ->
        val x0 = x + lead
        val yTop = y - top + rule / 2f
        val yBot = y + bottom
        val h = yBot - yTop
        val a = floatArrayOf(x0, yTop + 0.60f * h)
        val b = floatArrayOf(x0 + 0.12f * radW, yTop + 0.54f * h)
        val bt = floatArrayOf(x0 + 0.36f * radW, yBot)
        val d = floatArrayOf(x0 + radW, yTop)
        val path = Path().apply {
            moveTo(a[0], a[1]); lineTo(b[0], b[1]); lineTo(bt[0], bt[1]); lineTo(d[0], d[1])
            lineTo(x0 + radW + body.w + tail, d[1])
        }
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.MITER
        p.strokeWidth = rule
        cv.drawPath(path, p)
        p.strokeWidth = rule * 1.9f // the down-stroke is heavier, like the real radical sign
        cv.drawLine(b[0], b[1], bt[0], bt[1], p)
        p.style = Paint.Style.FILL
        body.draw(cv, x0 + radW, y)
        idx?.draw(cv, x0 + 0.45f * radW - idx.w, yTop + 0.42f * h)
    }
}

private fun layoutBoxed(n: MNode.Boxed, c: MathCtx): Lay {
    val b = layoutNode(n.body, c)
    val pad = 0.25f * c.size
    val rule = max(1.2f, 0.05f * c.size)
    val asc = max(b.asc, 0.5f * c.size)
    val w = b.w + 2f * pad
    val p = c.fonts.paint
    val color = c.color
    return Lay(w, asc + pad + rule / 2f, b.desc + pad + rule / 2f) { cv, x, y ->
        b.draw(cv, x + pad, y)
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = rule
        cv.drawRect(x + rule / 2f, y - asc - pad, x + w - rule / 2f, y + b.desc + pad, p)
        p.style = Paint.Style.FILL
    }
}

private fun delimLay(d: String, need: Float, c: MathCtx): Lay {
    if (d.isEmpty()) return Lay(0f, 0f, 0f, NO_DRAW)
    val base = glyph(d, Face.MAIN, c)
    val h0 = base.asc + base.desc
    if (h0 <= 0f || need <= h0 * 1.02f) return base
    val axis = 0.25f * c.size
    val k = need / h0
    val sx = min(1.6f, 1f + 0.18f * (k - 1f))
    val cy = (base.asc - base.desc) / 2f
    return Lay(base.w * sx, axis + need / 2f, need / 2f - axis) { cv, x, y ->
        cv.save()
        cv.translate(x, y - axis)
        cv.scale(sx, k)
        base.draw(cv, 0f, cy)
        cv.restore()
    }
}

private fun layoutDelim(n: MNode.Delim, c: MathCtx): Lay {
    val body = layoutNode(n.body, c)
    val axis = 0.25f * c.size
    val delta = max(body.asc - axis, body.desc + axis)
    val need = max(2f * delta * 0.95f, n.minEm * c.size)
    val l = delimLay(n.left, need, c)
    val r = delimLay(n.right, need, c)
    val w = l.w + body.w + r.w
    return Lay(w, max(body.asc, max(l.asc, r.asc)), max(body.desc, max(l.desc, r.desc))) { cv, x, y ->
        l.draw(cv, x, y)
        body.draw(cv, x + l.w, y)
        r.draw(cv, x + l.w + body.w, y)
    }
}

private fun layoutTable(n: MNode.Table, c: MathCtx): Lay {
    val cc = c.sub(display = n.display)
    val cells = n.rows.map { row -> row.map { layoutNode(it, cc) } }
    val nCols = cells.maxOfOrNull { it.size } ?: 0
    if (cells.isEmpty() || nCols == 0) return Lay(0f, 0f, 0f, NO_DRAW)

    val colW = FloatArray(nCols)
    cells.forEach { r -> r.forEachIndexed { j, l -> colW[j] = max(colW[j], l.w) } }
    val gaps = FloatArray(nCols)
    for (j in 0 until nCols - 1) {
        gaps[j] = c.size * (if (n.aligned) (if (j % 2 == 0) 0f else 1f) else 1f)
    }
    val rowA = cells.map { r -> max(0.7f * c.size, r.maxOfOrNull { it.asc } ?: 0f) }
    val rowD = cells.map { r -> max(0.3f * c.size, r.maxOfOrNull { it.desc } ?: 0f) }
    val rowSep = 0.2f * c.size
    val ys = FloatArray(cells.size)
    for (i in 1 until cells.size) ys[i] = ys[i - 1] + rowD[i - 1] + rowSep + rowA[i]
    val totalH = rowA[0] + ys.last() + rowD.last()
    val axis = 0.25f * c.size
    val asc = totalH / 2f + axis
    val desc = totalH / 2f - axis
    val firstBase = -asc + rowA[0]
    val edge = if (n.aligned) 0f else 0.2f * c.size
    val w = colW.sum() + gaps.sum() + 2f * edge
    return Lay(w, asc, max(0f, desc)) { cv, x, y ->
        for (i in cells.indices) {
            for (j in cells[i].indices) {
                val l = cells[i][j]
                var cx = x + edge
                for (k in 0 until j) cx += colW[k] + gaps[k]
                val dx = when (n.aligns.getOrElse(j) { 'c' }) {
                    'r' -> colW[j] - l.w
                    'l' -> 0f
                    else -> (colW[j] - l.w) / 2f
                }
                l.draw(cv, cx + dx, y + firstBase + ys[i])
            }
        }
    }
}

private fun layoutAccent(n: MNode.Accent, c: MathCtx): Lay {
    val b = layoutNode(n.body, c)
    val rule = max(1f, 0.04f * c.size)
    val gap = 0.1f * c.size
    val p = c.fonts.paint
    val color = c.color
    val size = c.size

    if (n.kind == "underline") {
        return Lay(b.w, b.asc, b.desc + gap + rule) { cv, x, y ->
            b.draw(cv, x, y)
            p.color = color; p.style = Paint.Style.FILL
            cv.drawRect(x, y + b.desc + gap, x + b.w, y + b.desc + gap + rule, p)
        }
    }
    val ah = when (n.kind) {
        "overline", "bar" -> rule
        "dot", "ddot" -> 0.1f * size
        "tilde" -> 0.16f * size
        else -> 0.2f * size
    }
    val single = b.w < size
    val aw = when (n.kind) {
        "overline" -> b.w
        "bar" -> if (single) min(b.w, 0.55f * size) else b.w
        else -> if (single) 0.5f * size else b.w
    }
    return Lay(b.w, b.asc + gap + ah, b.desc) { cv, x, y ->
        b.draw(cv, x, y)
        val cx = x + b.w / 2f
        val yb = y - b.asc - gap // bottom edge of the accent
        val x1 = cx - aw / 2f
        val x2 = cx + aw / 2f
        p.color = color
        p.strokeWidth = rule
        p.strokeJoin = Paint.Join.ROUND
        when (n.kind) {
            "overline", "bar" -> {
                p.style = Paint.Style.FILL
                cv.drawRect(x1, yb - rule, x2, yb, p)
            }
            "hat" -> {
                p.style = Paint.Style.STROKE
                val path = Path().apply { moveTo(x1, yb); lineTo(cx, yb - ah); lineTo(x2, yb) }
                cv.drawPath(path, p)
            }
            "tilde" -> {
                p.style = Paint.Style.STROKE
                val path = Path().apply {
                    moveTo(x1, yb - 0.2f * ah)
                    cubicTo(cx - aw * 0.15f, yb - ah * 1.4f, cx + aw * 0.15f, yb + ah * 0.4f, x2, yb - ah * 0.9f)
                }
                cv.drawPath(path, p)
            }
            "vec" -> {
                p.style = Paint.Style.STROKE
                val ym = yb - ah / 2f
                val head = 0.1f * size
                val path = Path().apply {
                    moveTo(x1, ym); lineTo(x2, ym)
                    moveTo(x2 - head, ym - head * 0.7f); lineTo(x2, ym); lineTo(x2 - head, ym + head * 0.7f)
                }
                cv.drawPath(path, p)
            }
            "dot" -> {
                p.style = Paint.Style.FILL
                cv.drawCircle(cx, yb - ah / 2f, 0.05f * size, p)
            }
            "ddot" -> {
                p.style = Paint.Style.FILL
                cv.drawCircle(cx - 0.12f * size, yb - ah / 2f, 0.05f * size, p)
                cv.drawCircle(cx + 0.12f * size, yb - ah / 2f, 0.05f * size, p)
            }
        }
        p.style = Paint.Style.FILL
    }
}

// ----------------------------------------------------------------------------
// Composables
// ----------------------------------------------------------------------------

// Display math: one or more centred equations (one per $$...$$ part), no box.
@Composable
fun LatexDisplay(parts: List<String>, color: Color, modifier: Modifier = Modifier) {
    val fonts = rememberMathFonts()
    val density = LocalDensity.current
    val sizePx = with(density) { 20.sp.toPx() }
    val argb = color.toArgb()
    val lays = remember(parts, fonts, sizePx, argb) {
        parts.map { layoutLatex(it, MathCtx(fonts, argb, sizePx, display = true)) }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lays.forEach { lay ->
            Box(Modifier.horizontalScroll(rememberScrollState())) { MathCanvas(lay) }
        }
    }
}

@Composable
internal fun MathCanvas(lay: Lay, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val pad = 2f
    val wDp = with(density) { (lay.w + 2 * pad).toDp() }
    val hDp = with(density) { (lay.asc + lay.desc + 2 * pad).toDp() }
    Canvas(modifier.size(wDp, hDp)) {
        drawIntoCanvas { lay.draw(it.nativeCanvas, pad, pad + lay.asc) }
    }
}

// Inline math inside a paragraph: the placeholder box ends at the text baseline
// (PlaceholderVerticalAlign.AboveBaseline), so we draw with the baseline at the bottom.
@Composable
internal fun MathInline(lay: Lay) {
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { lay.draw(it.nativeCanvas, 0f, size.height) }
    }
}
