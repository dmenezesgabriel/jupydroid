package com.jupydroid.app.ui.editor

/**
 * One row of the table of contents as it is drawn: the heading, whether it owns anything below it,
 * and whether that is folded away.
 *
 * The reference's own `TableOfContentsItem` renders a `TreeItem` with `expanded={!heading.collapsed}`
 * and draws a disclosure only where there are children (`packages/toc/src/tocitem.tsx`); these are
 * the two facts that decides, computed rather than stored.
 */
data class TocRow(val heading: TocHeading, val ownsSection: Boolean, val collapsed: Boolean)

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
fun List<TocHeading>.tocRows(collapsed: Set<String>): List<TocRow> {
  val rows = mutableListOf<TocRow>()
  // The level a fold is hiding *below*, or null while nothing is being hidden. A stack is not
  // needed: a heading at or above this level ends the fold, and if it opens one of its own it
  // replaces this - which is exactly what "until an equal or lower level" means.
  var hidingBelow: Int? = null
  forEachIndexed { at, heading ->
    if (hidingBelow != null && heading.level > hidingBelow) return@forEachIndexed
    hidingBelow = null
    val folded = heading.cellId in collapsed
    rows += TocRow(heading, ownsSection = ownsSectionAt(at), collapsed = folded)
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
  filterIndexedTo(mutableListOf()) { at, _ -> ownsSectionAt(at) }.mapTo(mutableSetOf()) { it.cellId }

/**
 * `someExpanded` - whether the one command's next tap folds or unfolds, and what its label reads
 * from (`packages/toc-extension/src/index.ts:174-210`).
 */
fun List<TocHeading>.tocSomeExpanded(collapsed: Set<String>): Boolean =
  tocSectionOwners().any { it !in collapsed }

/** Whether the heading at [at] is followed by a deeper one, which is what it would fold away. */
private fun List<TocHeading>.ownsSectionAt(at: Int): Boolean =
  (getOrNull(at + 1)?.level ?: this[at].level) > this[at].level
