package com.jupydroid.app.domain.model

/**
 * What a query found in one file, and what replacing those matches leaves behind.
 *
 * `IFileMatch` and `ITextSearchMatch` (`services/search/common/search.ts:195-221`), with the
 * matching itself here rather than in either adapter: the fake and the disk searcher have to agree
 * exactly, and the only way to guarantee that is for there to be one of it.
 */

/**
 * One file with something in it - `IFileMatch`, whose `resource` is a path here.
 *
 * A file with no match is never one of these: the reference emits a progress item *because* there
 * was a hit.
 */
data class MatchedFile(val path: String, val matches: List<MatchedLine>)

/**
 * One match: the line it is on, that line's text, and where in the text it sits.
 *
 * `ITextSearchMatch`'s `previewText` plus its `rangeLocations`, flattened - the reference groups
 * several ranges under one preview when they share a line, and a result list that draws one row
 * per match needs them apart anyway.
 *
 * @property line **one-based**, as `ISearchRange.startLineNumber` is.
 * @property text the whole line, which is what a result row shows.
 * @property range where the match is **within [text]**, not within the file.
 */
data class MatchedLine(val line: Int, val text: String, val range: IntRange)

/**
 * Every match in [text], line by line.
 *
 * **Line by line, and that is a limit rather than an implementation detail.** The reference
 * supports a pattern that spans lines, deciding so from the pattern itself
 * (`isMultilineRegexSource`, set onto `IPatternInfo.isMultiline`); a pattern containing a newline
 * simply finds nothing here. It is stated rather than hidden because the alternative - matching
 * over the whole file - would report a match no result row could draw, since a row is a line.
 *
 * ```
 * matchedLinesIn("import os\nprint(os)", Regex("os"))   // two matches, lines 1 and 2
 * ```
 */
fun matchedLinesIn(text: String, regex: Regex): List<MatchedLine> = text.split("\n").flatMapIndexed { at, line ->
  regex.findAll(line).map { MatchedLine(line = at + 1, text = line, range = it.range) }
}

/** A file's text after a replace, and how many matches that took. */
data class ReplacedText(val text: String, val count: Int)

/**
 * [text] with every match of [query] replaced, and the count of them.
 *
 * Line by line for the reason [matchedLinesIn] gives - a replace must touch exactly what a search
 * reported, and nothing beside it. `\n` is put back as it was taken, so a `\r\n` file keeps its
 * `\r` inside the line.
 *
 * A query that compiles to nothing changes nothing, rather than throwing: the box is blank or
 * half-typed for most of the time it is being typed.
 */
fun textWithMatchesReplaced(
  text: String,
  query: SearchQuery,
  replacement: String,
  preserveCase: Boolean,
): ReplacedText {
  val regex = (query.compile() as? CompiledQuery.Matching)?.regex ?: return ReplacedText(text, 0)
  var count = 0
  val replaced = text.split("\n").joinToString("\n") { line ->
    regex.replace(line) { match ->
      count++
      replacementFor(match.value, replacement, query, preserveCase)
    }
  }
  return ReplacedText(replaced, count)
}
