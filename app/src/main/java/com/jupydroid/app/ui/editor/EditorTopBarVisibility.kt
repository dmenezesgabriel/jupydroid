package com.jupydroid.app.ui.editor

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * Whether the editor keeps its app bar, or gives the notebook the 64dp instead.
 *
 * A phone in landscape is 320dp tall. Status bar, app bar, symbol row and keyboard add up to more
 * than that, and `Scaffold` resolves the overflow by handing the cell list **zero height** - so a
 * notebook being typed into showed no cell at all, and a `uiautomator` dump of that state
 * contained none (`evidence/u6-ime-layout/640dp/`). Nothing about it says "wrong"; it is simply
 * what fits, which is why only a screenshot on a short window could find it.
 *
 * The bar goes only while a cell is being edited, which is the only time the keyboard is there to
 * take the room. In command mode a short window keeps it, and back still leaves edit mode without
 * it (`EditorScreen`'s `BackHandler`), so the way out never depends on the arrow being on screen.
 *
 * ```
 * if (isEditorTopBarVisible(state.mode)) EditorTopBar(…)
 * ```
 */
internal fun editorTopBarVisible(heightDp: Int, mode: CellMode): Boolean =
  mode != CellMode.EDIT || heightDp >= MEDIUM_HEIGHT_DP

/** What the current window says, for callers with no reason to care how it is measured. */
@Composable
internal fun isEditorTopBarVisible(mode: CellMode): Boolean = mode != CellMode.EDIT ||
  currentWindowAdaptiveInfo().windowSizeClass.isHeightAtLeastBreakpoint(MEDIUM_HEIGHT_DP)

/** `WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND`, restated so the pure rule needs no android. */
internal const val MEDIUM_HEIGHT_DP = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
