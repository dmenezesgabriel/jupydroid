package com.jupydroid.app.syntax

/**
 * Shell, hand-lexed after the mode JupyterLab loads for `text/x-sh`: CodeMirror's legacy `shell`
 * (`@codemirror/legacy-modes@6.5.2 mode/shell.js`), itself a hand tokenizer rather than a grammar.
 *
 * Not ANTLR like [PythonSyntax] for the reason [JsonSyntax] gives: this scans and never fails, so
 * a half-written script still renders. The reference's classes land on the kinds a cell already
 * colours - `keyword` and `atom` on [SyntaxKind.KEYWORD], its command list (`builtin`) on
 * [SyntaxKind.FUNCTION], a backquote (`quote`) on [SyntaxKind.STRING], the shebang (`meta`) on
 * [SyntaxKind.COMMENT] - and an expansion or an assignment's name (`def`) is a
 * [SyntaxKind.VARIABLE]. Its `-flag` (`attribute`) and here-document classes have no colour here.
 *
 * ```
 * ShellSyntax().spansOf("echo \"\$HOME\"")
 * ```
 */
class ShellSyntax : SyntaxHighlighter {
  override fun spansOf(source: String): List<SyntaxSpan> = ShellScan(source).spans()
}

/** One pass over [source], left to right; [at] is where the next token starts. */
private class ShellScan(private val source: String) {
  private val found = mutableListOf<SyntaxSpan>()
  private var at = 0

  fun spans(): List<SyntaxSpan> {
    while (at < source.length) step()
    return found
  }

  private fun step() {
    val ch = source[at]
    when {
      // A backslash takes the next character with it, so `\$HOME` expands nothing.
      ch == '\\' -> at = minOf(at + 2, source.length)
      ch == '\'' || ch == '`' -> quoted(ch)
      ch == '"' -> doubleQuoted()
      ch == '#' && atWordStart() -> comment()
      ch == '$' -> expansion()
      ch.isShellWordChar() -> word()
      else -> at++
    }
  }

  /** Bash reads `a#b` as one word: `#` opens a comment only where a word could begin. */
  private fun atWordStart(): Boolean = at == 0 || source[at - 1].isWhitespace() || source[at - 1] in WORD_BREAKS

  /** `'…'` and `` `…` `` to their closing mark, or to the end; neither colours what is inside. */
  private fun quoted(mark: Char) {
    val close = source.indexOf(mark, at + 1)
    val end = if (close < 0) source.length else close + 1
    add(at, end, SyntaxKind.STRING)
    at = end
  }

  /** `"…"`, split around each expansion in it - the reference's `tokenString` hands `$` to `tokenDollar`. */
  private fun doubleQuoted() {
    var segment = at
    at++
    while (at < source.length && source[at] != '"') {
      if (source[at] == '$' && at + 1 < source.length && source[at + 1].opensExpansion()) {
        add(segment, at, SyntaxKind.STRING)
        expansion()
        segment = at
      } else {
        at = minOf(at + if (source[at] == '\\') 2 else 1, source.length)
      }
    }
    at = minOf(at + 1, source.length)
    add(segment, at, SyntaxKind.STRING)
  }

  private fun comment() {
    val newline = source.indexOf('\n', at)
    val end = if (newline < 0) source.length else newline
    add(at, end, SyntaxKind.COMMENT)
    at = end
  }

  /** `$name`, `$1`, `$?` and the rest of the specials, or `${…}`; a `$` alone is still marked. */
  private fun expansion() {
    val start = at
    at++
    val next = source.getOrNull(at)
    at = when {
      next == '{' -> source.indexOf('}', at).let { if (it < 0) source.length else it + 1 }
      next != null && (next.isDigit() || next in SPECIAL_PARAMETERS) -> at + 1
      else -> skipWhile(at) { it.isLetterOrDigit() || it == '_' }
    }
    add(start, at, SyntaxKind.VARIABLE)
  }

  /** A word, which is an assignment's name before `=`, a number when all digits, or a listed name. */
  private fun word() {
    val start = at
    at = skipWhile(at) { it.isShellWordChar() }
    val text = source.substring(start, at)
    val kind = when {
      source.getOrNull(at) == '=' && ASSIGNABLE.matches(text) -> SyntaxKind.VARIABLE
      text.all { it.isDigit() } -> SyntaxKind.NUMBER
      text in KEYWORDS -> SyntaxKind.KEYWORD
      text in COMMANDS -> SyntaxKind.FUNCTION
      else -> return
    }
    add(start, at, kind)
  }

  private fun skipWhile(from: Int, accept: (Char) -> Boolean): Int {
    var end = from
    while (end < source.length && accept(source[end])) end++
    return end
  }

  private fun add(start: Int, end: Int, kind: SyntaxKind) {
    if (end > start) found.add(SyntaxSpan(start, end, kind))
  }
}

/** The reference's `eatWhile(/[\w-]/)`: a word may carry a dash, as `my-tool` does. */
private fun Char.isShellWordChar(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

private fun Char.opensExpansion(): Boolean =
  isLetterOrDigit() || this == '_' || this == '{' || this in SPECIAL_PARAMETERS

private const val WORD_BREAKS = ";|&()"

private const val SPECIAL_PARAMETERS = "?@#$!*-"

private val ASSIGNABLE = Regex("[A-Za-z_][A-Za-z0-9_]*")

/**
 * The reference's `commonKeywords` and `commonAtoms`, plus `case`: its list closes a `case` with
 * `esac` but never opens one, and a reader would not colour the two apart.
 */
private val KEYWORDS = setOf(
  "if", "then", "do", "else", "elif", "while", "until", "for", "in", "esac", "fi", "fin", "fil",
  "done", "exit", "set", "unset", "export", "function", "case", "true", "false",
)

/** The reference's `commonCommands`, verbatim. */
private val COMMANDS = setOf(
  "ab", "awk", "bash", "beep", "cat", "cc", "cd", "chown", "chmod", "chroot", "clear", "cp", "curl", "cut",
  "diff", "echo", "find", "gawk", "gcc", "get", "git", "grep", "hg", "kill", "killall", "ln", "ls", "make",
  "mkdir", "openssl", "mv", "nc", "nl", "node", "npm", "ping", "ps", "restart", "rm", "rmdir", "sed",
  "service", "sh", "shopt", "shred", "source", "sort", "sleep", "ssh", "start", "stop", "su", "sudo", "svn",
  "tee", "telnet", "top", "touch", "vi", "vim", "wall", "wc", "wget", "who", "write", "yes", "zsh",
)
