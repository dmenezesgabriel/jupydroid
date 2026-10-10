package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.plainText

/**
 * The cell holding the heading a `#fragment` link names, or null when no heading has that name.
 *
 * Two names are tried, each across the whole notebook in document order, as `querySelector`
 * answers the first match:
 * - JupyterLab's id, the one its `¶` anchor carries ([headingAnchorId]), which is case-kept and so
 *   tells `C` from `C++`;
 * - then VS Code's slug of both sides ([headingSlug]), so a link written for GitHub or VS Code -
 *   `#whats-new` for "What's new?" - lands too. VS Code's `-1` suffix for a repeated heading is not
 *   kept: the first of two equal headings wins, as it does under the id.
 *
 * Every heading on screen is a candidate: each top-level heading of a markdown cell, not only the
 * one [Cell.headingLevel] picks, and every one an output draws whether or not the table of contents
 * lists them, since JupyterLab's lookup searches the whole document.
 *
 * ```
 * state.cellOfHeadingAnchor("Results") // "c7"
 * ```
 */
fun EditorUiState.cellOfHeadingAnchor(fragment: String): String? {
  val headings = headingsOnScreen()
  return headings.firstOrNull { (_, text) -> headingAnchorId(text) == fragment }?.first
    ?: headingSlug(fragment).takeIf { it.isNotEmpty() }?.let { slug ->
      headings.firstOrNull { (_, text) -> headingSlug(text) == slug }?.first
    }
}

/** Each drawn heading's text beside the id of the cell it is drawn in, in document order. */
private fun EditorUiState.headingsOnScreen(): List<Pair<String, String>> = cells.flatMap { cell ->
  when (cell.kind) {
    CellKind.MARKDOWN -> MarkdownDocument.parse(cell.source).filterIsInstance<RichBlock.Heading>()
      .map { cell.id to it.inlines.plainText() }
    CellKind.CODE -> outputHeadingsOf(cell.id).map { cell.id to it.text }
    // Nothing renders a raw cell, so a heading in one is not on screen to land on.
    CellKind.RAW -> emptyList()
  }
}

/**
 * `createHeaderId`: the heading's text, every space a `-`
 * (jupyterlab@7f0e690 `packages/rendermime/src/renderers.ts`).
 */
internal fun headingAnchorId(text: String): String = text.replace(' ', '-')

/**
 * `slugify` (vscode@3f3380c6 `extensions/markdown-language-features/notebook/index.ts`), less its
 * closing `encodeURI`: both sides of the comparison are slugged here, so escaping both would
 * change nothing.
 */
internal fun headingSlug(text: String): String = text.trim().lowercase()
  .replace(WHITESPACE_RUN, "-")
  .replace(SLUG_PUNCTUATION, "")
  .trim('-')

/**
 * Unicode-aware, as JavaScript's `\s` is: `\p{Z}` adds the space separators, the no-break space
 * among them, that the JVM's `\s` leaves out. Not the JVM's `(?U)` flag, which ART's ICU engine
 * refuses with a `PatternSyntaxException` (`HeadingSlugOnDeviceTest`).
 */
private val WHITESPACE_RUN = Regex("""[\s\p{Z}]+""")

/** VS Code's "known punctuators", the same set in the same file as [headingSlug]'s. */
private val SLUG_PUNCTUATION =
  Regex("""[\]\[!/'"#$%&()*+,./:;<=>?@\\^{|}~`。，、；：？！…—·ˉ¨‘’“”々～‖∶＂＇｀｜〃〔〕〈〉《》「」『』．〖〗【】（）［］｛｝]""")
