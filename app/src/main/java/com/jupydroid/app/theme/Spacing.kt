package com.jupydroid.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The gaps this app draws, on Material's 4dp grid.
 *
 * **One name per gap, because before this there were none.** Measured across `ui/` before it was
 * written: 63 `.dp` literals in 20 files, 16 distinct values, 13 of them off the grid. No single
 * one of those was wrong; what was wrong is that the next gap was chosen by eye against whatever
 * a neighbouring file happened to use, which is how `14.dp` came to sit beside `16.dp` in the
 * explorer and its recents.
 *
 * Six steps, and deliberately not more: a scale with a step for every measurement is the literals
 * again with longer names. Something that does not fit is either a *size* rather than a gap - a
 * pane's width, a popup's cap - or a value copied from a reference, and both of those state
 * themselves at the point of use with a comment saying where they came from. That distinction is
 * what `scripts/check_ui_dimensions.py` enforces.
 *
 * ```
 * Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
 * ```
 */
object Spacing {
  /** Between a glyph and the word it belongs to; the tightest gap that still reads as a gap. */
  val xs: Dp = 4.dp

  /** Within a control - a row's internal padding, the space between an icon and its label. */
  val sm: Dp = 8.dp

  /** Between controls that belong to one group. */
  val md: Dp = 12.dp

  /** A surface's own margin, and the gap between two things that do not belong together. */
  val lg: Dp = 16.dp

  /** Between sections of one surface. */
  val xl: Dp = 24.dp

  /** Around something that stands alone - an empty state, a dialog's content. */
  val xxl: Dp = 32.dp

  /** The scale in order, for [com.jupydroid.app.theme.SpacingTest] and for nothing else. */
  val steps: Map<String, Dp> = mapOf(
    "xs" to xs,
    "sm" to sm,
    "md" to md,
    "lg" to lg,
    "xl" to xl,
    "xxl" to xxl,
  )
}
