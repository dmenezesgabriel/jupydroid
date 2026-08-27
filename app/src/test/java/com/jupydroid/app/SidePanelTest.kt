package com.jupydroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which panel the side area shows, and the two rules that are not Compose's own: picking the panel
 * already showing collapses the area rather than doing nothing, and what a **one-pane** window is
 * left showing when it has no rail to collapse from.
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

  /**
   * **What the bottom bar offers, where the rail offers everything.** The table of contents is not
   * on it, and that is P2o-4's own argument rather than a shortening: Files is a screen you
   * navigate *to*, and the table of contents is a surface over the document you are already *in* -
   * which is why `toc:show-panel` opens an overlay from the notebook's own menu at this width
   * (`TableOfContentsPanel`) and why it needs no destination beside it.
   *
   * **Two is below Material's own floor** of *"three to five destinations of equal importance"*
   * (`developer.android.com/develop/ui/compose/components/navigation-bar`), and that is recorded in
   * `docs/DECISIONS.md` rather than glossed - a third item that is not a destination would satisfy
   * the count by breaking the sentence it comes from.
   */
  @Test
  fun theBarOffersTheTwoPanelsThatAreDestinations() {
    assertEquals(listOf(SidePanel.FILES, SidePanel.SEARCH), onePaneDestinations)
  }

  /** And it is a sub-list of the rail's, in the rail's order - one rank order, not two. */
  @Test
  fun theBarKeepsTheRailsOrder() {
    assertEquals(SidePanel.entries.filter { it in onePaneDestinations }, onePaneDestinations)
  }

  /**
   * **A one-pane window shows the panel it was left on**, which is the half P2x-2b could not give:
   * with no bar there, every panel had to fall back to the browser or become unreachable.
   */
  @Test
  fun aOnePaneWindowKeepsAPanelTheBarCanLeave() {
    assertEquals(SidePanel.FILES, SidePanel.FILES.showingAtOnePane())
    assertEquals(SidePanel.SEARCH, SidePanel.SEARCH.showingAtOnePane())
  }

  /**
   * **P2x-2b's defect, narrowed rather than reopened.** A tablet rotated from 1280dp to 800dp with
   * the table of contents up used to keep drawing it with no rail to leave it by, and the browser
   * could not be reached again without killing the app. The bar has no table-of-contents item, so
   * that panel is still the one case that falls back - and the fall-back is the browser.
   */
  @Test
  fun aPanelWithNoItemOnTheBarFallsBackToTheBrowser() {
    assertEquals(SidePanel.FILES, SidePanel.TABLE_OF_CONTENTS.showingAtOnePane())
  }

  /**
   * **A collapsed area is not a state one pane has.** `collapse()` leaves the activity bar visible
   * over an empty area (`packages/application/src/shell.ts:2383-2394`), which is coherent beside a
   * document and is an empty window without one.
   */
  @Test
  fun aCollapsedAreaNarrowsToTheBrowserRatherThanToNothing() {
    val collapsed: SidePanel? = null

    assertEquals(SidePanel.FILES, collapsed.showingAtOnePane())
  }

  /** Whatever it is handed, it draws something - there is no fourth answer for a caller to miss. */
  @Test
  fun everyPanelNarrowsToOneThatIsDrawn() {
    (SidePanel.entries + null).forEach { panel ->
      assertTrue("$panel narrowed to something the bar cannot leave", panel.showingAtOnePane() in onePaneDestinations)
    }
  }
}
