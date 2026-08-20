package com.jupydroid.app.ui.editor

/**
 * One row of the table of contents as it is drawn: the heading, whether it owns anything below it,
 * and whether that is folded away.
 *
 * The reference's own `TableOfContentsItem` renders a `TreeItem` with `expanded={!heading.collapsed}`
 * and draws a disclosure only where there are children (`packages/toc/src/tocitem.tsx`); these are
 * the two facts that decides, computed rather than stored.
 */
data class TocRow(
  val heading: TocHeading,
  val ownsSection: Boolean,
  val collapsed: Boolean,
  /** `toc:display-numbering`'s answer for this entry, or `""` - see [tocPrefixes]. */
  val prefix: String = "",
)

/**
 * The rows a table of contents shows, given which of its entries are folded.
 *
 * **The tree is derived at draw time from the flat list**, which is the reference's own shape:
 * `buildTree` takes as a heading's children the ones that follow it until an equal or lower level
 * appears (`packages/toc/src/toctree.tsx:57-102`) - the same rule [headingSectionOf] states for a
 * heading's section in the notebook, one rank along. Nothing records that a row is hidden, so an
 * edit that adds, removes or re-levels a heading is correct here without knowing this exists.
 *
 * Deriving it also gives the reference's `localCollapsed` branch for free: unfolding a parent over
 * a still-folded child shows the child and leaves the child's own children hidden, because that
 * child is still folded on its own account.
 *
 * ```
 * headings.tocRows(collapsed = setOf("chapter-one"))  // the chapter, and nothing under it
 * ```
 */
fun List<TocHeading>.tocRows(collapsed: Set<String>, numbering: TocNumbering = TocNumbering()): List<TocRow> {
  // Numbered over *every* heading and not only the drawn ones: the numbers are the document's, so
  // what a fold hides still counts. The reference splits it the same way - the model numbers, the
  // view folds.
  val prefixes = tocPrefixes(numbering)
  val rows = mutableListOf<TocRow>()
  // The level a fold is hiding *below*, or null while nothing is being hidden. A stack is not
  // needed: a heading at or above this level ends the fold, and if it opens one of its own it
  // replaces this - which is exactly what "until an equal or lower level" means.
  var hidingBelow: Int? = null
  forEachIndexed { at, heading ->
    if (hidingBelow != null && heading.level > hidingBelow) return@forEachIndexed
    hidingBelow = null
    val folded = heading.key in collapsed
    rows += TocRow(heading, ownsSectionAt(at), collapsed = folded, prefix = prefixes[at])
    if (folded) hidingBelow = heading.level
  }
  return rows
}

/**
 * `toc:toggle-collapse`'s operand - every entry that owns something, which is what Collapse All
 * folds (`packages/toc-extension/src/index.ts:174-210`).
 *
 * **Narrowed from the reference, which marks every heading.** A leaf's flag hides nothing either
 * way, and keeping it would leave [tocSomeExpanded] permanently true in a notebook with no
 * nesting - so the one control would collapse for ever and never expand.
 */
fun List<TocHeading>.tocSectionOwners(): Set<String> =
  filterIndexedTo(mutableListOf()) { at, _ -> ownsSectionAt(at) }.mapTo(mutableSetOf()) { it.key }

/**
 * `someExpanded` - whether the one command's next tap folds or unfolds, and what its label reads
 * from (`packages/toc-extension/src/index.ts:174-210`).
 */
fun List<TocHeading>.tocSomeExpanded(collapsed: Set<String>): Boolean = tocSectionOwners().any { it !in collapsed }

/** Whether the heading at [at] is followed by a deeper one, which is what it would fold away. */
private fun List<TocHeading>.ownsSectionAt(at: Int): Boolean =
  (getOrNull(at + 1)?.level ?: this[at].level) > this[at].level
