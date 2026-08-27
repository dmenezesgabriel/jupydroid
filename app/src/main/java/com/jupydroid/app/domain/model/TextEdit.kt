package com.jupydroid.app.domain.model

/**
 * A document changed at the ranges a search found.
 *
 * This is the half of replace that `WorkspaceSearcher` deliberately does not carry. P2x-1e took
 * `replaceAll` off the port because the reference replaces through the *editor model* stack rather
 * than through the filesystem: `IReplaceService.replace` builds one `ResourceTextEdit` per match
 * from **the matches already on screen** and hands them to `IBulkEditService`
 * (`replaceService.ts:115-118`). So the shape a replace needs is not "a query and a file" but
 * "a range and what goes in it", which is what lives here.
 *
 * **Not what P2x-1e deleted.** `textWithMatchesReplaced` and `ReplacedText` came off the port
 * because they took a *query* and re-ran the search, so a file changed in between would have been
 * rewritten against matches nobody saw. These take the matches themselves and refuse when the
 * document has moved on, which is the opposite arrangement.
 */

/**
 * One range of a document replaced by [text] - `ResourceTextEdit`'s `{ range, text }` payload.
 *
 * @property range where in the document, **inclusive of both ends**, as every range this app passes
 *   around is (`MatchedLine.range`, `SearchHit.range`).
 */
data class TextEdit(val range: IntRange, val text: String)

/**
 * This text with every one of [edits] applied, keeping what lies between them.
 *
 * **Sorted here rather than required of the caller**, which is the reference's own arrangement:
 * `applyEditsToPreview` sorts the operations it built by where they start before pushing them
 * (`replaceService.ts:196-203`). A caller assembling edits from several places should not have to
 * know the order matters.
 *
 * The ranges must not overlap - a search's matches never do, since `findAll` walks forward.
 *
 * ```
 * "cat and cat".withEditsApplied(listOf(TextEdit(0..2, "dog")))   // "dog and cat"
 * ```
 */
fun String.withEditsApplied(edits: List<TextEdit>): String {
  val out = StringBuilder()
  var lastEnd = 0
  edits.sortedBy { it.range.first }.forEach { edit ->
    out.append(substring(lastEnd, edit.range.first)).append(edit.text)
    lastEnd = edit.range.last + 1
  }
  return out.append(substring(lastEnd)).toString()
}

/**
 * The edits these matches imply against [document], or **null when it no longer holds them**.
 *
 * One [TextEdit] per match, each carrying what [replacementFor] makes of it - so the two-step rule
 * (substitute, then preserve case) reaches a workspace replace whole rather than being re-derived.
 *
 * **Null is the version check, adapted.** The reference compares a model's `versionId` against the
 * one the edit was built from and throws *"has changed in the meantime"* on any difference
 * (`bulkTextEdits.ts:213-216`); this app has no version counter for a file on disk, and it needs
 * one more than the reference does - the workspace is shared with the kernel, so a cell can
 * `open(…, 'w')` a file between the walk and the tap. Comparing the characters is the equivalent
 * that holds without one, and it is **stronger than `MatchedLine.rangeIn` alone**: a line still
 * present and still long enough, now saying something else, has a range and the wrong text in it.
 *
 * **One stale match refuses them all**, because the reference refuses the whole bulk edit rather
 * than the offending resource - `_validateBeforePrepare` throws before a single task is prepared.
 *
 * ```
 * matchedLinesIn(text, regex).editsAgainst(text, "dog", query, preserveCase = false)
 * ```
 */
fun List<MatchedLine>.editsAgainst(
  document: String,
  replaceText: String,
  query: SearchQuery,
  preserveCase: Boolean,
): List<TextEdit>? {
  val edits = mapNotNull { it.editAgainst(document, replaceText, query, preserveCase) }
  return edits.takeIf { it.size == size }
}

/** One match as an edit, or null when [document] does not still read the way the walk read it. */
private fun MatchedLine.editAgainst(
  document: String,
  replaceText: String,
  query: SearchQuery,
  preserveCase: Boolean,
): TextEdit? {
  val at = stillAt(document) ?: return null
  return TextEdit(at, replacementFor(text.substring(range), replaceText, query, preserveCase))
}

/**
 * Where this match sits in [document] **if [document] still holds it**, and null otherwise.
 *
 * The version check itself, split off from what a match becomes - two questions, and detekt asked
 * for the seam by counting returns, exactly as it did for `asSearchRegex` and `startOf`. Both
 * halves are needed: `rangeIn` answers whether the line is still there and long enough, and the
 * comparison answers whether it still says what was found.
 */
private fun MatchedLine.stillAt(document: String): IntRange? {
  val at = rangeIn(document) ?: return null
  return at.takeIf { document.substring(it) == text.substring(range) }
}
