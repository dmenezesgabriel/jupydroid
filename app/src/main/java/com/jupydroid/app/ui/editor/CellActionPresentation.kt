package com.jupydroid.app.ui.editor

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/** Which surface a cell's actions are offered on. */
enum class CellActionPresentation {
  /** A `ModalBottomSheet`: full-width rows within thumb reach. */
  SHEET,

  /** A `DropdownMenu` anchored to the cell's own button. */
  MENU,
}

/**
 * The rule, as a pure function of window width.
 *
 * A dropdown anchored to a cell near the top of a 6" screen is a stretch for a thumb and its rows
 * are sized for a mouse; on a tablet the same menu is right and a sheet would be heavier than the
 * job needs. One list of actions, two presentations.
 *
 * [MEDIUM_WIDTH_DP] is the documented compact/medium boundary. **A width rule, never a device
 * one** - a tablet in a 480dp split-screen window is a compact window, and the class changes at
 * runtime as the user drags the divider.
 */
internal fun cellActionPresentationFor(widthDp: Int): CellActionPresentation =
  if (widthDp >= MEDIUM_WIDTH_DP) CellActionPresentation.MENU else CellActionPresentation.SHEET

/** What the current window says, for callers that have no reason to care how it is measured. */
@Composable
internal fun currentCellActionPresentation(): CellActionPresentation =
  if (currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(MEDIUM_WIDTH_DP)) {
    CellActionPresentation.MENU
  } else {
    CellActionPresentation.SHEET
  }

/** `WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND`, restated so the pure rule needs no android. */
private const val MEDIUM_WIDTH_DP = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
