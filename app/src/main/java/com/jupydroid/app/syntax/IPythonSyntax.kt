package com.jupydroid.app.syntax

/**
 * IPython highlighting for a notebook cell or the console: Python, plus the magics
 * `pythonLanguage` never colours - JupyterLab hands `text/x-ipython` the same plain Python
 * mode it gives `.py` files (`packages/codemirror/src/language.ts`), which upstream counts as
 * a bug (jupyterlab#3869).
 *
 * The roles come from `usr/lib/python3.12/site-packages/ipython_pygments_lexers.py`: the
 * `%`/`%%` sigil is an `Operator` ([SyntaxKind.MAGIC]), the magic's name a `Keyword`
 * ([SyntaxKind.KEYWORD]) and the args plain `Text`. The placement rules come from the kernel
 * itself, `usr/lib/python3.12/site-packages/IPython/core/inputtransformer2.py`, because those
 * are the rules that decide what actually executes: `cell_magic` fires on
 * `lines[0].startswith("%%")` only, `EscapedCommand` takes a `%` as a line's first non-INDENT
 * token, and `MagicAssign` takes a `%` that follows a bare `=` at paren depth zero.
 *
 * A `%%` cell's body is delegated the way the reference does it - `%%bash`-family cells are
 * shell, `%%markdown` is markdown, `%%time`/`%%capture`/`%%python*` and `%%!` stay Python and
 * shell respectively, and a name with no language we can paint keeps the body plain rather than
 * claiming a colouring we cannot render.
 *
 * ```
 * IPythonSyntax().spansOf("%matplotlib inline")
 * ```
 */
class IPythonSyntax : SyntaxHighlighter {
  override fun spansOf(source: String): List<SyntaxSpan> = when {
    source.isEmpty() -> emptyList()
    // `%%` fires on lines[0] column zero, and position zero cannot sit inside a string or
    // comment - so a source that opens with `%%` is a cell magic and nothing else.
    source.startsWith("%%") -> cellMagic(source)
    else -> pythonWithLineMagics(source)
  }

  /**
   * A `%%` cell is the magic's domain end to end: sigil and name on the first line, the args
   * plain, and every later line handed to the language the magic's name runs.
   */
  private fun cellMagic(source: String): List<SyntaxSpan> {
    val name = cellMagicName(source)
    val found = mutableListOf(SyntaxSpan(0, 2, SyntaxKind.MAGIC))
    if (name.isNotEmpty()) found.add(SyntaxSpan(2, 2 + name.length, SyntaxKind.KEYWORD))
    val lineEnd = source.indexOf('\n')
    if (lineEnd < 0) return found
    val body = bodyLanguage(name).spansOf(source.substring(lineEnd + 1))
    body.forEach { found.add(SyntaxSpan(it.start + lineEnd + 1, it.end + lineEnd + 1, it.kind)) }
    return found
  }

  /** `(\w+)` in the reference, plus `%%!`, which its own table delegates to `BashLexer`. */
  private fun cellMagicName(source: String): String {
    val end = wordEnd(source, 2)
    return when {
      end > 2 -> source.substring(2, end)
      source.getOrNull(2) == '!' -> "!"
      else -> ""
    }
  }

  private fun pythonWithLineMagics(source: String): List<SyntaxSpan> {
    val python = PythonSyntax().spansOf(source)
    // The scanner is line-based where ipython_pygments_lexers is stateful, so a `%` on a line
    // inside a triple-quoted string or a comment would false-light without the mask.
    val masked = python.filter { it.kind == SyntaxKind.STRING || it.kind == SyntaxKind.COMMENT }
    val scan = LineMagicScan(source, masked).also { it.run() }
    if (scan.spans.isEmpty()) return python
    val kept = python.filter { span -> scan.suppressed.none { (from, until) -> span.start in from until until } }
    return (kept + scan.spans).sortedBy { it.start }
  }

  /**
   * The language a `%%` cell's body is highlighted as - a fixed map rather than classic
   * notebook's user-extensible `highlight_modes`, covering the highlighters `syntax/` ships.
   * `pypy` and `!` are the reference's own rows (`ipython_pygments_lexers.py`'s `%%pypy` and
   * `%%!` entries); everything else unknown is plain, as the reference leaves it `Text`.
   */
  private fun bodyLanguage(name: String): SyntaxHighlighter = when {
    name in SHELL_BODIES -> ShellSyntax()
    name in MARKDOWN_BODIES -> MarkdownSyntax()
    name in PYTHON_BODIES || name.startsWith("python") -> PythonSyntax()
    else -> PlainText
  }

  private companion object {
    val SHELL_BODIES = setOf("bash", "sh", "script", "sx", "system", "!")
    val MARKDOWN_BODIES = setOf("markdown")
    val PYTHON_BODIES = setOf("capture", "debug", "file", "prun", "pypy", "time", "timeit", "writefile")
  }

  /**
   * One pass over the physical lines, carrying paren depth between them, looking for the two
   * sigil shapes the kernel honours. [masked] is the STRING/COMMENT spans the Python pass
   * already found; [suppressed] collects each magic line's tail, whose args are plain `Text`
   * and so must lose whatever Python painted there.
   */
  private class LineMagicScan(private val source: String, private val masked: List<SyntaxSpan>) {
    val spans = mutableListOf<SyntaxSpan>()
    val suppressed = mutableListOf<Pair<Int, Int>>()
    private var depth = 0

    fun run() {
      var lineStart = 0
      while (lineStart < source.length) {
        val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
        val sigil = lineMagicSigil(lineStart, lineEnd)
        if (sigil >= 0) paintLineMagic(sigil, lineEnd)
        updateDepth(lineStart, if (sigil >= 0) sigil else lineEnd)
        lineStart = lineEnd + 1
      }
    }

    /**
     * Rule A, `EscapedCommand`: a `%` that opens the line's real content (INDENT is skipped, so
     * indentation is allowed) is a line magic - unless it is `%%`, which is inert anywhere but
     * line zero column zero.
     *
     * Rule B, `MagicAssign`: a `%` that directly follows the line's first bare `=` at paren
     * depth zero. One `=` is all the kernel checks, so one is all this checks.
     */
    private fun lineMagicSigil(lineStart: Int, lineEnd: Int): Int {
      val first = firstNonWhitespace(lineStart, lineEnd)
      if (first != null && depth == 0 && isSigilAt(first)) return first
      val afterAssign = firstAssignAtTopLevel(lineStart, lineEnd)
        ?.let { firstNonWhitespace(it + 1, lineEnd) }
        ?.takeIf { isSigilAt(it) }
      return afterAssign ?: -1
    }

    /** The kernel's `_find_assign_op`: the first `=` not inside brackets, never `==` or `+=`. */
    private fun firstAssignAtTopLevel(lineStart: Int, lineEnd: Int): Int? {
      var level = depth
      for (at in lineStart until lineEnd) {
        if (maskedCovers(at)) continue
        when (source[at]) {
          '(', '[', '{' -> level++
          ')', ']', '}' -> level--
          '=' -> if (level == 0 && isBareAssign(at)) return at
        }
      }
      return null
    }

    private fun paintLineMagic(sigil: Int, lineEnd: Int) {
      spans.add(SyntaxSpan(sigil, sigil + 1, SyntaxKind.MAGIC))
      val nameEnd = wordEnd(source, sigil + 1)
      if (nameEnd > sigil + 1) spans.add(SyntaxSpan(sigil + 1, nameEnd, SyntaxKind.KEYWORD))
      suppressed.add(sigil to lineEnd)
    }

    private fun isSigilAt(at: Int): Boolean = source[at] == '%' && source.getOrNull(at + 1) != '%' && !maskedCovers(at)

    /** `=` rather than `==`, `<=`, `+=`, `:=` or the rest of the operator family. */
    private fun isBareAssign(at: Int): Boolean =
      source.getOrNull(at + 1) != '=' && (source.getOrNull(at - 1) ?: ' ') !in ASSIGN_PREFIXES

    private fun firstNonWhitespace(from: Int, until: Int): Int? =
      (from until until).firstOrNull { !source[it].isWhitespace() }

    private fun maskedCovers(at: Int): Boolean = masked.any { at >= it.start && at < it.end }

    /** Paren depth carries across lines: a `%` inside `x = (\n%ls\n)` is an error, not a magic. */
    private fun updateDepth(from: Int, until: Int) {
      for (at in from until until) {
        if (maskedCovers(at)) continue
        when (source[at]) {
          '(', '[', '{' -> depth++
          ')', ']', '}' -> depth--
        }
      }
    }
  }
}

/** Chars that may precede an `=` without making it an assignment - `+=`, `:=`, `==` and kin. */
private const val ASSIGN_PREFIXES = "=<>!+-*/%&|^~:@"

/** The reference's `(\w+)` run: a magic's name is letters, digits and `_`. */
private fun wordEnd(source: String, from: Int): Int {
  var end = from
  while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) end++
  return end
}
