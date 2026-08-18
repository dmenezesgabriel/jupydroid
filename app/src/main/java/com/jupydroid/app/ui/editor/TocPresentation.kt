package com.jupydroid.app.ui.editor

import androidx.window.core.layout.WindowSizeClass

/** Where the table of contents is drawn. */
enum class TocPresentation {
  /**
   * A column beside the notebook that stays open across jumps, which is what the reference's own
   * ToC is - a sidebar panel, not a menu (`packages/toc-extension/src/index.ts:309`).
   */
  PANE,

  /** A menu or a sheet over the notebook, dismissed by the pick - see [TableOfContentsPanel]. */
  OVERLAY,
}

/**
 * The rule, as a pure function of the width the **editor itself** was given.
 *
 * Not the window's width, and that distinction is the rule rather than a detail of it: in the
 * two-pane layout the explorer has already taken 360dp, so an 840dp *window* leaves the editor
 * about 456dp - and a ToC carved out of that would leave the notebook narrower than the phone the
 * single-pane layout was designed for. So the caller measures the room it actually has and passes
 * it in, which is also what makes both branches reachable from a test: a window cannot be forced
 * under a composable (`docs/DECISIONS.md`), while a layout constraint can.
 *
 * [PANE_WIDTH_DP] is the expanded lower bound - the number this app already means by "wide enough
 * to split in two" (`PaneDirective.kt`), applied one level down to the pane doing the splitting.
 * At 840dp the notebook keeps 536dp beside a [TOC_PANE_WIDTH] column, which is wider than either
 * device this project drives.
 */
internal fun tocPresentationFor(widthDp: Int): TocPresentation =
  if (widthDp >= PANE_WIDTH_DP) TocPresentation.PANE else TocPresentation.OVERLAY

/** `WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND`, restated so the pure rule needs no android. */
private const val PANE_WIDTH_DP = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
