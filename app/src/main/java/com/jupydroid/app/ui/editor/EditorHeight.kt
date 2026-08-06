package com.jupydroid.app.ui.editor

/**
 * Turns the height a page reports into one a cell can safely be.
 *
 * **The unit is dp, not physical pixels.** The page measures `scrollHeight` in CSS pixels,
 * and with `width=device-width, initial-scale=1` one CSS pixel is one dp - so the number
 * crosses the bridge already density-independent and must not be divided by density again.
 * A 1x emulator hides this completely: at 160 dpi px, dp and CSS px are all the same number,
 * and the mistake only appears on a real device.
 *
 * The page is also the side most likely to be wrong by accident - a layout loop, a font that
 * has not loaded - so its number is clamped rather than believed. The page does its own 4 px
 * threshold and 80 ms debounce; this is only the bounds.
 */
object EditorHeight {
  /** Below this a cell cannot comfortably be tapped into, and an empty one reports almost nothing. */
  const val MINIMUM_DP = 48

  /** Matches the page's own cap, so the two cannot disagree about what runaway means. */
  const val MAXIMUM_DP = 8000

  fun accept(reportedCssPx: Int): Int = reportedCssPx.coerceIn(MINIMUM_DP, MAXIMUM_DP)
}
