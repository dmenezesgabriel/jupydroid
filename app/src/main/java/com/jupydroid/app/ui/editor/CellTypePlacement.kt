package com.jupydroid.app.ui.editor

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * Whether the notebook's bar names the active cell's type itself, or leaves it to the overflow.
 *
 * **Two homes for one control, because the reference has two.** `cellType` is a notebook-toolbar
 * item at rank 40 (`packages/notebook-extension/schema/panel.json`), and that toolbar is a
 * `ReactiveToolbar` (`packages/apputils/src/mainareawidget.ts:51`): an item that does not fit is
 * *moved* into a popup opener rather than dropped, later ranks first
 * (`packages/ui-components/src/components/toolbar.tsx:570-700`). This app's popup opener is
 * [NotebookActionsMenu] - the one overflow a phone's bar has room for.
 *
 * **Measured before the number was chosen.** Free width in this app's action row, between the
 * navigation icon and the first action, read off `evidence/p2o-3-two-pane-threshold/<w>/hierarchy.xml`
 * (the emulator is density 1, so 1px is 1dp): **72dp at 320dp**, **552dp at 800dp**, ~567dp at
 * 1280dp. "Markdown" plus a caret wants about 110dp, so the phone is the width that cannot have
 * it and both of the tablet's orientations can, with room to spare either way.
 *
 * [MEDIUM_WIDTH_DP] is the documented compact/medium boundary, stated rather than computed - the
 * call [cellActionPresentationFor] and [editorTopBarVisible] each make. **A width rule, never a
 * device one**, for their reason: a tablet in a 480dp split-screen window is a compact window, and
 * the class changes at runtime as the divider is dragged.
 */
internal fun cellTypeOnBar(widthDp: Int): Boolean = widthDp >= MEDIUM_WIDTH_DP

/** What the current window says, for callers with no reason to care how it is measured. */
@Composable
internal fun isCellTypeOnBar(): Boolean =
  currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(MEDIUM_WIDTH_DP)

/** `WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND`, restated so the pure rule needs no android. */
private const val MEDIUM_WIDTH_DP = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
