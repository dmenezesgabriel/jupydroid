package com.jupydroid.app.domain.model

/**
 * What a query found in one file.
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
 * @property line **one-based**, as `ISearchRange.startLineNumber` is - and within [cell]'s own
 *   source where there is one, because two cells both have a line 1.
 * @property text the whole line, which is what a result row shows.
 * @property range where the match is **within [text]**, not within the file.
 * @property cell which cell of a notebook this is in, **zero-based** - `INotebookCellMatchNoModel`'s
 *   own `index` (`search/common/searchNotebookHelpers.ts:18-22`). Null in a plain file, which is
 *   every match there was before P2x-4.
 *
 *   **The index and not the cell's id, and that is a measurement rather than a preference.** The
 *   reference falls back to `rawCell#${index}` wherever there is no live cell model to ask
 *   (`searchNotebookHelpers.ts:20`, `notebookSearchModel.ts:162`), and here the id is worse than
 *   merely absent: `CellFile.toDomain` mints a fresh `UUID` for a cell that carries none
 *   (nbformat's id is optional before 4.5), so the id the walk read and the id the editor loaded
 *   need not be the same string. The index is.
 */
data class MatchedLine(val line: Int, val text: String, val range: IntRange, val cell: Int? = null)

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

/**
 * Every match in a notebook's **cells**, in document order, each naming the cell it is in.
 *
 * The `contentResults` half of `INotebookCellMatchNoModel` (`searchNotebookHelpers.ts:18-22`): one
 * pass per cell over that cell's source, stamped with the cell's index. A notebook read as the JSON
 * it is stored in would report `"cell_type"` and the inside of a base64 image as matches, which is
 * what `DiskWorkspaceSearcher` skipped every `.ipynb` to avoid until this existed.
 *
 * **Source only - `webviewResults` is not built here.** JupyterLab's own output filter is off by
 * default (`packages/notebook/src/searchprovider.ts:229-243`) and this app cannot yet land on an
 * output match from outside the notebook: what text an output shows is decided by
 * `ui/editor/RenderedOutput`, which `data` may not import, and `SearchHit.outputAt` indexes the
 * *rendered* outputs rather than the stored ones. `docs/DECISIONS.md` carries it.
 *
 * ```
 * matchedCellsIn(notebook, Regex("cat"))   // [MatchedLine(1, "print(cat)", 6..8, cell = 1)]
 * ```
 */
fun matchedCellsIn(notebook: Notebook, regex: Regex): List<MatchedLine> =
  notebook.cells.flatMapIndexed { at, cell -> matchedLinesIn(cell.source, regex).map { it.copy(cell = at) } }

/**
 * Where this match sits in [text] as a whole, or null when [text] no longer holds it.
 *
 * What opening a result has to land on: `searchView.open` builds a `selection` from the match's own
 * range and hands it to `openEditor` (`searchView.ts:2240`), so the editor reveals the match *and*
 * shows which characters were found. A [MatchedLine] carries offsets within its own line, and this
 * is the arithmetic from there to the file's.
 *
 * **Null rather than a guess when the file has moved on.** The workspace is shared with the kernel,
 * so a cell can rewrite a file between the walk and the tap. The reference refuses a stale match the
 * same way, throwing *"has changed in the meantime"* on a version mismatch
 * (`replaceService.ts:213-216`); selecting whatever now happens to sit at those offsets would be
 * worse than landing at the top of the file.
 *
 * ```
 * MatchedLine(2, "print(cat)", 6..8).rangeIn("import os\nprint(cat)\n")   // 16..18
 * ```
 */
fun MatchedLine.rangeIn(text: String): IntRange? {
  val start = text.split("\n").startOf(this) ?: return null
  return (start + range.first)..(start + range.last)
}

/**
 * Where [match]'s line begins in these lines, or null when they no longer hold it.
 *
 * Its own function because the two halves answer different questions - whether the line is still
 * there and long enough, and how far into the file it starts. detekt asked for the split by counting
 * returns, and the seam it landed on is the right one, exactly as it was for `asSearchRegex`.
 */
private fun List<String>.startOf(match: MatchedLine): Int? {
  val line = getOrNull(match.line - 1) ?: return null
  // Every earlier line and the newline that ended it.
  return if (match.range.last >= line.length) null else take(match.line - 1).sumOf { it.length + 1 }
}
