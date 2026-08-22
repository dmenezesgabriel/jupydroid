package com.jupydroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which panel the side area shows, and the one rule that is not Compose's own: picking the panel
 * already showing collapses the area rather than doing nothing.
 */
class SidePanelTest {
  @Test
  fun pickingAnotherPanelSelectsIt() {
    assertEquals(SidePanel.TABLE_OF_CONTENTS, SidePanel.FILES.chosen(SidePanel.TABLE_OF_CONTENTS))
    assertEquals(SidePanel.FILES, SidePanel.TABLE_OF_CONTENTS.chosen(SidePanel.FILES))
  }

  /**
   * `collapse()` sets `this._sideBar.currentTitle = null` and, in `'side'` mode, *"only deselects
   * the active tab, leaving the activity bar visible"* (`packages/application/src/shell.ts:2383-2394`)
   * - which is what `application:toggle-left-area` drives
   * (`packages/application-extension/src/index.tsx:514-534`).
   */
  @Test
  fun pickingThePanelAlreadyShowingCollapsesTheArea() {
    assertNull(SidePanel.FILES.chosen(SidePanel.FILES))
    assertNull(SidePanel.TABLE_OF_CONTENTS.chosen(SidePanel.TABLE_OF_CONTENTS))
  }

  /** And the same control expands it again, which is the other half of the toggle. */
  @Test
  fun pickingFromACollapsedAreaSelects() {
    val collapsed: SidePanel? = null

    assertEquals(SidePanel.FILES, collapsed.chosen(SidePanel.FILES))
  }

  /**
   * The reference's own rank order, which is what the rail draws top to bottom: File Browser at
   * rank 100 (`packages/filebrowser-extension/src/index.ts:676`) and Table of Contents at 400
   * (`packages/toc-extension/src/index.ts:309`).
   */
  @Test
  fun theOrderIsTheReferencesRankOrder() {
    assertEquals(listOf(SidePanel.FILES, SidePanel.TABLE_OF_CONTENTS), SidePanel.entries)
  }
}
