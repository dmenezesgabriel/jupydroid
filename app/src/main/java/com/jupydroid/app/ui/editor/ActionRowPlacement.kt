package com.jupydroid.app.ui.editor

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * Whether the notebook's action row has the width to carry a control's **label**, or must reduce it.
 *
 * **One rule, two controls, and it is the same question about the same row.** The active cell's
 * type moves into the overflow where its label does not fit ([CellTypeControl],
 * [NotebookActionsMenu]); the kernel indicator keeps its status glyph and drops the kernel's name
 * ([KernelIndicator]). Both are a *labelled* control on a row with four icons and a navigation
 * arrow already on it. It was `cellTypeOnBar` in a file named for that one control until a second
 * reader arrived.
 *
 * **Both homes are the reference's, not an invention.** `cellType` is a notebook-toolbar item at
 * rank 40 and `kernelName` one at rank 1000 (`packages/notebook-extension/schema/panel.json`), and
 * that toolbar is a `ReactiveToolbar` (`packages/apputils/src/mainareawidget.ts:51`): an item that
 * does not fit is *moved* into a popup opener rather than dropped
 * (`packages/ui-components/src/components/toolbar.tsx:570-700`). This app's popup opener is
 * [NotebookActionsMenu] - the one overflow a phone's bar has room for. **What the kernel indicator
 * departs from is the reference's *order*:** `_getWidgetsToRemove` sheds the rightmost items first,
 * so the reference would push the execution indicator into the popup *before* the kernel name.
 * A phone is this app's primary device and the kernel's state is what has to stay glanceable
 * there, so the name goes and the glyph stays.
 *
 * **Measured before the number was chosen.** Free width in this app's action row, between the
 * navigation icon and the first action, read off the view hierarchy at each width
 * (the emulator is density 1, so 1px is 1dp): **72dp at 320dp**, **552dp at 800dp**, ~567dp at
 * 1280dp. "Markdown" plus a caret wants about 110dp, and so does a status glyph plus "Python 3",
 * so the phone is the width that cannot have either and both of the tablet's orientations can.
 *
 * [MEDIUM_WIDTH_DP] is the documented compact/medium boundary, stated rather than computed - the
 * call [cellActionPresentationFor] and [editorTopBarVisible] each make. **A width rule, never a
 * device one**, for their reason: a tablet in a 480dp split-screen window is a compact window, and
 * the class changes at runtime as the divider is dragged.
 */
internal fun labelsFitOnActionRow(widthDp: Int): Boolean = widthDp >= MEDIUM_WIDTH_DP

/** What the current window says, for callers with no reason to care how it is measured. */
@Composable
internal fun labelsFitOnActionRow(): Boolean =
  currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(MEDIUM_WIDTH_DP)

/** `WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND`, restated so the pure rule needs no android. */
private const val MEDIUM_WIDTH_DP = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
