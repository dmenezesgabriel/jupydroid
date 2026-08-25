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
   * The order the rail draws top to bottom, and **it takes both references to state it** - which
   * is the correction P2x-2 made to its own row in `docs/PLAN.md`.
   *
   * - The file browser is first in both: rank 100
   *   (`packages/filebrowser-extension/src/index.ts:676`), `order: 0`
   *   (`vscode/…/files/browser/explorerViewlet.ts:265`).
   * - **Search is second, and only VS Code says so**: `order: 1` on the sidebar view container
   *   (`vscode/…/search/browser/search.contribution.ts:59`), directly after the explorer's 0.
   *   JupyterLab registers no search panel at all - `shell.add(…, 'left', …)` is called five times
   *   in its whole tree and none of them is one.
   * - **The table of contents is last, and only JupyterLab says so**: rank 400
   *   (`packages/toc-extension/src/index.ts:309`). VS Code has no such container - its Outline is
   *   a view *inside* the explorer's (`vscode/…/outline/browser/outline.contribution.ts:26-38`).
   *
   * So the two answer different halves and agree where they overlap.
   */
  @Test
  fun theOrderIsWhatTheTwoReferencesTogetherSay() {
    assertEquals(
      listOf(SidePanel.FILES, SidePanel.SEARCH, SidePanel.TABLE_OF_CONTENTS),
      SidePanel.entries,
    )
  }

  /** Every panel toggles, including the one P2x-2 added - `chosen` is written over the type. */
  @Test
  fun everyPanelCollapsesTheAreaWhenItIsTheOneShowing() {
    SidePanel.entries.forEach { panel -> assertNull(panel.chosen(panel)) }
  }
}
