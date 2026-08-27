package com.jupydroid.app.ui.shell

import com.jupydroid.app.domain.model.MatchedLine

/**
 * One result row's text, split where the match is - `MatchImpl.preview()`'s `before` / `inside` /
 * `after` (`searchTreeModel/match.ts:65-80`).
 *
 * A data class rather than three calls, because the three parts are decided *together*: the cap is
 * spent left to right, so how much of [after] survives depends on how long [before] and [match]
 * came out.
 */
data class MatchPreview(val before: String, val match: String, val after: String)

/**
 * What a result row shows for one match: the line trimmed and cut down to the reference's budget.
 *
 * ```
 * previewOf(MatchedLine(1, "    print(cat)", 10..12))   // MatchPreview("print(", "cat", ")")
 * ```
 */
fun previewOf(line: MatchedLine): MatchPreview {
  val fullBefore = line.text.substring(0, line.range.first)
  val before = lcut(fullBefore, PREVIEW_CONTEXT_CHARS, ELLIPSIS)
  // `charsRemaining` spent left to right, exactly as the reference spends it - the context first,
  // then as much of the match as is left, then whatever the tail can still have.
  val forMatch = MAX_PREVIEW_CHARS - before.length
  val match = line.text.substring(line.range.first, line.range.last + 1).take(forMatch)
  return MatchPreview(before, match, line.text.substring(line.range.last + 1).take(forMatch - match.length))
}

/**
 * `lcut` (`vscode/src/vs/base/common/strings.ts:771-794`), transcribed rather than approximated.
 *
 * Leading whitespace goes, and a line longer than [n] is cut **at a word boundary**, keeping the
 * *last* boundary whose tail is still [n] characters or longer - so the context nearest the match
 * is what survives and the cut never lands mid-word. The reference keeps *at least* [n] rather
 * than at most, which is the half a paraphrase gets backwards.
 */
internal fun lcut(text: String, n: Int, prefix: String = ""): String {
  val trimmed = text.trimStart()
  val cut = if (trimmed.length < n) 0 else lastBoundaryKeeping(trimmed, n)
  return if (cut == 0) trimmed else prefix + trimmed.substring(cut).trimStart()
}

/**
 * The last `\b` in [text] with at least [n] characters after it, or 0 when there is none.
 *
 * The reference's `while (re.test(trimmed))` loop, including its `re.lastIndex += 1` - a zero-width
 * match would otherwise be found at the same position forever.
 */
private fun lastBoundaryKeeping(text: String, n: Int): Int {
  var found = 0
  var next = WORD_BOUNDARY.find(text, 0)?.range?.first
  // Both of the reference's exits in the one condition: no boundary left, or the next one is too
  // far in to keep [n] characters after it.
  while (next != null && text.length - next >= n) {
    found = next
    // `re.lastIndex += 1` - a zero-width match would otherwise be found at the same place forever.
    next = WORD_BOUNDARY.find(text, next + 1)?.range?.first
  }
  return found
}

/** `/\b/` - the same word-character class in both languages, so the boundaries land in one place. */
private val WORD_BOUNDARY = Regex("\\b")

/** `lcut(fullBefore, 26, '…')` - how much of the line before a match a row keeps. */
private const val PREVIEW_CONTEXT_CHARS = 26

/** `MatchImpl.MAX_PREVIEW_CHARS` (`searchTreeModel/match.ts:22`). */
internal const val MAX_PREVIEW_CHARS = 250

/** The reference's own `'…'`, one character rather than three dots. */
private const val ELLIPSIS = "…"
