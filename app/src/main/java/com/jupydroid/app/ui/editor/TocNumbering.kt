package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.EditorSettings

/**
 * Whether a table of contents numbers its entries, and whether the top level joins in.
 *
 * `toc:display-numbering` and `toc:display-h1-numbering`, whose defaults are the schema's:
 * `numberHeaders` **false** and `numberingH1` **true**
 * (`packages/toc-extension/schema/registry.json:32-69`).
 *
 * `baseNumbering` and `maximalDepth` are settings there with **no command** and no surface here
 * yet, and neither is invented early. `baseNumbering`'s difference would be cosmetic anyway -
 * it changes the number only where `level === highestLevel`
 * (`packages/toc/src/utils/common.ts:172`). `maximalDepth` bounds which headings the list keeps
 * at all, and `tableOfContentsHeadings` keeping every level is `docs/architecture.md` §11's
 * `GAP-043`.
 */
data class TocNumbering(val numberHeaders: Boolean = false, val numberingH1: Boolean = true)

/**
 * The same two settings as this screen reads them. Here rather than on [EditorSettings], because
 * `domain` may not import `ui` - the guard-tested rule in `docs/architecture.md` §5.
 */
fun EditorSettings.tocNumbering() = TocNumbering(tocNumberHeaders, tocNumberingH1)

/**
 * The numbering prefix for each heading, in order and one per heading - `""` where nothing is
 * numbered, so a caller can zip the two lists without checking.
 *
 * `getPrefix` under `filterHeadings`' own loop (`packages/toc/src/utils/common.ts:157-196` and
 * `:29-60`). Both halves matter and neither is obvious:
 *
 * - **One running counter threaded across the whole document.** The reference passes the same
 *   `levels` array through every cell, which is what makes the numbers the *document's* rather
 *   than each cell's - so this takes the whole list rather than one heading.
 * - **Descending opens a counter, returning increments and truncates.** A deeper level starts at 1
 *   (or at [BASE_NUMBERING] when it is the highest numbered level); coming back up increments that
 *   level and **drops** everything deeper, which is `levels.splice(level)`.
 * - **A skipped level counts as zero rather than closing up** - the reference's own comment,
 *   *"If the header list skips some level, replace missing elements by 0"*, so an H1 followed by an
 *   H3 numbers the H3 `1.0.1.`
 *
 * With `numberingH1` off the top level is dropped from the string entirely (`levels.slice(1)`) and
 * a heading with nothing open above it gets no prefix at all.
 *
 * ```
 * headings.tocPrefixes(TocNumbering(numberHeaders = true))  // ["1. ", "1.1. ", "2. "]
 * ```
 */
fun List<TocHeading>.tocPrefixes(numbering: TocNumbering): List<String> {
  if (!numbering.numberHeaders) return List(size) { "" }
  val levels = mutableListOf<Int>()
  // `previousLevel = levels.length` in the reference, which is 0 for a document starting fresh.
  var previousLevel = 0
  return map { heading ->
    val prefix = levels.prefixFor(heading.level, previousLevel, numbering.numberingH1)
    previousLevel = heading.level
    prefix
  }
}

private fun MutableList<Int>.prefixFor(level: Int, previousLevel: Int, numberingH1: Boolean): String {
  if (level > previousLevel) {
    // The reference zeroes the levels between the last heading and this one in a loop of its own
    // (`for (let l = previousLevel; l < level - 1; l++) levels[l] = 0`). That loop is redundant
    // here and measuring is what said so: [setLevel] pads with zeroes, and `size` is always the
    // previous heading's level - the ascending branch grows it to exactly `level` and the
    // descending one truncates to it - so those slots never exist to hold anything but the pad.
    // The reference opens the counter at `baseNumbering` for the highest numbered level and at 1
    // for every other (`level === highestLevel ? baseNumbering : 1`, where `highestLevel` is 1 or
    // 2 depending on `numberingH1`). `baseNumbering` is a schema setting with **no command**, and
    // its default is 1 - so both arms are the same number here and only one is written. The
    // distinction comes back if the setting ever does.
    setLevel(level - 1, 1)
  } else {
    setLevel(level - 1, getOrElse(level - 1) { 0 } + 1)
    // Coming back up closes every level below this one.
    if (level < previousLevel) while (size > level) removeAt(size - 1)
  }
  return when {
    numberingH1 -> joinToString(".") + ". "
    size > 1 -> drop(1).joinToString(".") + ". "
    // Nothing is open above this one, so with the top level unnumbered there is nothing to say.
    else -> ""
  }
}

/** JavaScript grows an array on assignment past its end; this is that, with the gap filled by 0. */
private fun MutableList<Int>.setLevel(at: Int, value: Int) {
  while (size <= at) add(0)
  this[at] = value
}
