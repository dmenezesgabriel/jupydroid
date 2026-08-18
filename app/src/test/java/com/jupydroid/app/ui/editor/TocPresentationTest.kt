package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the table of contents is drawn, given how much room the *editor itself* has.
 *
 * The reference's own ToC is a persistent sidebar panel
 * (`packages/toc-extension/src/index.ts:309`, `shell.add(toc, 'left')`), and a panel is worth
 * more than a menu for exactly the reason it is one there: a list you jump from repeatedly, and
 * that says where you are while the document scrolls, should not have to be reopened each time.
 * Below that width it stays the [TableOfContentsPanel] overlay, which is what P2h-2 shipped.
 *
 * **The width measured is the editor's, not the window's**, and that is the whole point of the
 * rule. In a two-pane layout the explorer has already taken 360dp, so an 840dp *window* leaves
 * the editor about 456dp - and a ToC carved out of that would leave the notebook narrower than
 * the phone the single-pane layout was designed for. Asking the window would get exactly this
 * case wrong, which is why the caller measures its own pane and passes it in.
 */
class TocPresentationTest {
  @Test
  fun aPhoneWidthEditorGetsTheOverlay() {
    assertEquals(TocPresentation.OVERLAY, tocPresentationFor(widthDp = 411))
  }

  /**
   * The tablet this project develops against: a 1280dp window less the 360dp explorer and the
   * 24dp spacer leaves the editor 896dp, so it is the pane it gets.
   */
  @Test
  fun aTabletWidthEditorGetsThePane() {
    assertEquals(TocPresentation.PANE, tocPresentationFor(widthDp = 896))
  }

  /**
   * 840dp is the expanded lower bound - the number this app already means by "wide enough to
   * split in two", applied one level down to the pane doing the splitting. "At least" includes
   * it, as `cellActionPresentationFor` reads its own boundary.
   */
  @Test
  fun theBoundaryItselfCountsAsRoomForAPane() {
    assertEquals(TocPresentation.PANE, tocPresentationFor(widthDp = 840))
    assertEquals(TocPresentation.OVERLAY, tocPresentationFor(widthDp = 839))
  }

  /**
   * The case a *window* rule would get wrong, and the reason this one is not a window rule: an
   * 840dp window in the two-pane layout leaves the editor about 456dp, which is an overlay.
   */
  @Test
  fun anEditorSharingAnExpandedWindowWithTheExplorerIsTreatedAsNarrow() {
    assertEquals(TocPresentation.OVERLAY, tocPresentationFor(widthDp = 456))
  }
}
