package com.jupydroid.app.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the notebook's action row has room to *label* a control, given how wide it is.
 *
 * **The rule exists because the reference has one.** `cellType` (rank 40) and `kernelName`
 * (rank 1000) are both notebook-toolbar items (`packages/notebook-extension/schema/panel.json`)
 * and that toolbar is a `ReactiveToolbar`: an item that does not fit is moved into a popup opener
 * rather than dropped (`packages/ui-components/src/components/toolbar.tsx:570-700`). So there are
 * two homes for such a control and this is what picks between them - for the cell type since
 * P2r, and for the kernel's name since P2w-2.
 *
 * **Measured before it was written.** Free width in this app's action row, between the navigation
 * icon and the first action, read off the committed hierarchies at
 * `evidence/p2o-3-two-pane-threshold/<w>/hierarchy.xml`: **72dp at 320dp**, **552dp at 800dp**,
 * ~567dp at 1280dp. The widest label the control can draw is "Markdown" plus a caret, which wants
 * about 110dp - so 320dp is the width that cannot have it and the tablet's two orientations both
 * can, with room to spare either way.
 *
 * Breakpoint is the documented compact/medium boundary, which is what
 * [cellActionPresentationFor] and [editorTopBarVisible] each state rather than compute. **Never a
 * device check**, for their reason: a tablet in a 480dp split-screen window is a compact window,
 * and the class changes at runtime as the divider is dragged.
 */
class ActionRowPlacementTest {
  @Test
  fun aPhoneWidthWindowHasNoRoomForALabel() {
    assertFalse(labelsFitOnActionRow(widthDp = 320))
  }

  @Test
  fun aTabletInEitherOrientationHasRoomForOne() {
    assertTrue(labelsFitOnActionRow(widthDp = 800))
    assertTrue(labelsFitOnActionRow(widthDp = 1280))
  }

  /** 600dp is the medium lower bound, and "at least" includes it. */
  @Test
  fun theBoundaryItselfCountsAsRoom() {
    assertTrue(labelsFitOnActionRow(widthDp = 600))
    assertFalse(labelsFitOnActionRow(widthDp = 599))
  }

  /** The case a device check gets wrong - see [cellActionPresentationFor]'s own. */
  @Test
  fun aTabletSharingItsScreenIsTreatedAsNarrow() {
    assertFalse(labelsFitOnActionRow(widthDp = 480))
  }
}
