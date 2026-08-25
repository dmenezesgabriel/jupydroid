package com.jupydroid.app.glob

/**
 * One glob pattern, matched against a whole path.
 *
 * `parseRegExp` + `toRegExp` (`src/vs/base/common/glob.ts:108-256`, `:525-536`), which is the
 * language both of the reference's exclude settings are written in and the language its search
 * boxes accept.
 *
 * **Transcribed rather than handed to `java.nio`'s `"glob:"` `PathMatcher`, and a measurement is
 * why.** A leading globstar there requires a separator to be present: on this machine's JDK,
 * `getPathMatcher("glob:` globstar `/node_modules")` matches `a/node_modules` and does **not**
 * match `node_modules`. Every one of the reference's eight registered exclude defaults is written
 * that way, so the platform matcher would have quietly failed to exclude a top-level
 * `node_modules` while excluding a nested one - a search that misses files, or reports them, with
 * nothing to say it did.
 *
 * Compiled once, matched many - a workspace walk asks the same pattern of every path it reaches.
 *
 * ```
 * PathGlob(globstar + slash + starDotPy).matches("reports/deep/notes.py")   // true
 * ```
 *
 * The example is spelled rather than written: Kotlin nests block comments, so a literal glob
 * carrying a slash-star opens one inside this doc.
 *
 * @param pattern the glob, with `/` as its separator - paths here are workspace-relative and
 *   POSIX throughout, so the reference's `[/\\]` is narrowed to the one this app can produce.
 */
class PathGlob(private val pattern: String) {
  /**
   * `Regex.matches` **is** `toRegExp`'s `^…$` - it requires the whole input, and this regex has no
   * `MULTILINE`, so the two are the same assertion with one less thing to get wrong.
   */
  private val regex = Regex(globRegexOf(pattern))

  fun matches(path: String): Boolean = regex.matches(path)

  override fun toString(): String = pattern
}

/**
 * `parseRegExp` - the pattern as the regular expression source it denotes.
 *
 * Internal rather than private because [PathGlob] is not its only caller: a brace choice is itself
 * a pattern, and the reference recurses here for exactly that (`glob.ts:203`).
 */
internal fun globRegexOf(pattern: String): String {
  if (pattern.isEmpty()) return ""
  val segments = splitGlobAware(pattern, '/')
  // *"Special case where we only have globstars"* - the whole thing is `.*`.
  return if (segments.all { it == GLOBSTAR }) ".*" else segmentsRegex(segments)
}

/** The `/`-separated segments, joined - `parseRegExp`'s `forEach` over them (`glob.ts:126-247`). */
private fun segmentsRegex(segments: List<String>): String {
  val source = StringBuilder()
  segments.forEachIndexed { at, segment ->
    val afterAGlobstar = at > 0 && segments[at - 1] == GLOBSTAR
    when {
      // *"if we have more than one globstar after another, just ignore it"*.
      segment == GLOBSTAR && afterAGlobstar -> Unit
      segment == GLOBSTAR -> source.append(starsRegex(2, isLast = at == segments.lastIndex))
      else -> source.append(segmentRegex(segment)).append(tailSeparator(segments, at))
    }
  }
  return source.toString()
}

/**
 * `starsToRegExp` (`glob.ts:51-64`).
 *
 * One star is *"any number of characters except path separator"*, non-greedy. Two are the
 * separator-crossing group, which gains a `/`-then-value alternative when nothing follows it -
 * that is what lets a trailing globstar match a file rather than only a directory.
 */
private fun starsRegex(stars: Int, isLast: Boolean): String = when (stars) {
  0 -> ""
  1 -> "$NO_PATH*?"
  else -> "(?:$PATH|$NO_PATH+$PATH${if (isLast) "|$PATH$NO_PATH+" else ""})*?"
}

/**
 * The separator a segment keeps because more is coming (`glob.ts:236-244`).
 *
 * Dropped only when the next segment is a globstar and there is nothing after it, because the
 * globstar's own group already begins with one. The reference gives the case this prevents: a
 * pattern rooted at `some` must not match a folder called `something`.
 */
private fun tailSeparator(segments: List<String>, at: Int): String {
  if (at >= segments.lastIndex) return ""
  val nextIsTheLastGlobstar = segments[at + 1] == GLOBSTAR && at + 2 >= segments.size
  return if (nextIsTheLastGlobstar) "" else PATH
}

/** One `/`-free segment, character by character - the inner loop of `parseRegExp` (`:150-232`). */
private fun segmentRegex(segment: String): String {
  val source = StringBuilder()
  var at = 0
  while (at < segment.length) {
    at = when (segment[at]) {
      '{' -> appendBrace(segment, at, source)
      '[' -> appendBracket(segment, at, source)
      '?' -> source.append(NO_PATH).let { at + 1 }
      '*' -> source.append(starsRegex(1, isLast = false)).let { at + 1 }
      else -> source.append(escapedForRegex(segment[at])).let { at + 1 }
    }
  }
  return source.toString()
}

/**
 * *"Converts {foo,bar} => [foo|bar]"* (`glob.ts:200-212`), each choice parsed as a pattern of its
 * own.
 *
 * **An unterminated brace swallows the rest of the segment**, which is what the reference's
 * `inBraces` state does rather than an accident of it: `braceVal` accumulates and nothing is ever
 * appended. A pattern being typed is unterminated for most of the time it is being typed, so this
 * is the ordinary case and not the exceptional one - the same reasoning `CompiledQuery.Invalid`
 * carries for a half-written regex.
 *
 * @return the index just past the closing brace, or the segment's end when there is none.
 */
private fun appendBrace(segment: String, at: Int, source: StringBuilder): Int {
  val close = segment.indexOf('}', at + 1)
  if (close < 0) return segment.length
  val choices = splitGlobAware(segment.substring(at + 1, close), ',')
  source.append(choices.joinToString(separator = "|", prefix = "(?:", postfix = ")") { globRegexOf(it) })
  return close + 1
}

/**
 * A character class (`glob.ts:214-220`), with the reference's three special cases inside it.
 *
 * Unterminated, it swallows the rest for the reason [appendBrace] gives.
 *
 * @return the index just past the closing bracket, or the segment's end when there is none.
 */
private fun appendBracket(segment: String, at: Int, source: StringBuilder): Int {
  val body = StringBuilder()
  var scan = at + 1
  while (scan < segment.length) {
    val char = segment[scan]
    // *"] is literally only allowed as first character in brackets to match it"*.
    if (char == ']' && body.isNotEmpty()) {
      source.append('[').append(body).append(']')
      return scan + 1
    }
    body.append(bracketChar(char, isFirst = body.isEmpty()))
    scan++
  }
  return segment.length
}

/**
 * One character inside `[…]` (`glob.ts:165-186`): the range operator kept as itself, a leading
 * negation normalised to `^`, a separator **dropped** - *"glob split matching is not allowed
 * within character ranges"*, per `glob(7)` - and everything else escaped.
 */
private fun bracketChar(char: Char, isFirst: Boolean): String = when {
  char == '-' -> "-"
  isFirst && (char == '^' || char == '!') -> "^"
  char == '/' -> ""
  else -> escapedForRegex(char)
}

/**
 * `splitGlobAware` (`glob.ts:66-108`) - split on [splitChar], except where it is enclosed.
 *
 * What makes it aware: the `,` in `*.{py,txt}` separates two choices and the `,` between two
 * patterns separates two patterns, and only the nesting tells them apart.
 *
 * ```
 * splitGlobAware("*.{py,txt}, notes.md", ',')   // ["*.{py,txt}", " notes.md"]
 * ```
 */
internal fun splitGlobAware(pattern: String, splitChar: Char): List<String> {
  if (pattern.isEmpty()) return emptyList()
  val segments = mutableListOf<String>()
  val current = StringBuilder()
  val nesting = GlobNesting()
  for (char in pattern) {
    if (char == splitChar && nesting.isSplittable) {
      segments += current.toString()
      current.clear()
      continue
    }
    nesting.saw(char)
    current.append(char)
  }
  // *"Tail"* - and an empty one is dropped, so a trailing separator adds no segment.
  if (current.isNotEmpty()) segments += current.toString()
  return segments
}

/**
 * The reference's `inBraces` and `inBrackets` as the one question they answer.
 *
 * **Two independent flags, not one enclosing character**, and that is deliberate: `{a],b}` leaves
 * `inBraces` set in the reference, so its `,` is still protected. Collapsing them into a single
 * "what am I inside" would unprotect it, which is a divergence on exactly the malformed input a
 * pattern is while it is being typed.
 */
private class GlobNesting {
  private var inBraces = false
  private var inBrackets = false

  val isSplittable: Boolean get() = !inBraces && !inBrackets

  fun saw(char: Char) {
    when (char) {
      '{' -> inBraces = true
      '}' -> inBraces = false
      '[' -> inBrackets = true
      ']' -> inBrackets = false
    }
  }
}

/** `escapeRegExpCharacters` (`src/vs/base/common/strings.ts:89-91`), as the set it denotes. */
private fun escapedForRegex(char: Char): String = if (char in REGEX_SPECIALS) "\\$char" else "$char"

private val REGEX_SPECIALS = "\\{}*+?|^$.[]()".toSet()

/** `GLOBSTAR`. */
private const val GLOBSTAR = "**"

/**
 * `PATH_REGEX` and `NO_PATH_REGEX`, narrowed to `/`: the reference accepts a backslash separator
 * because Windows produces one, and every path this app matches is workspace-relative POSIX.
 */
private const val PATH = "/"

private const val NO_PATH = "[^/]"
