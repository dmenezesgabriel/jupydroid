package com.jupydroid.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.theme.DarkChromePalette
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The side pane's own edge: drawn where there is a document beside it, and not where there is not.
 *
 * `PartBorderTest` holds what [com.jupydroid.app.ui.shell.trailingBorder] draws; this holds *when*
 * the side bar asks for it. The two are apart because the width rule is the half that could be
 * right in the modifier and wrong in the pane - the reference draws `sideBar.border` on the side
 * *separating to the editor* (`workbench/common/theme.ts:623`), and at one pane there is no editor
 * beside this and the trailing edge is the screen's own.
 *
 * `twoPane` is passed in, never measured: `currentWindowAdaptiveInfo()` reads the activity's
 * window, so forcing a size would drive one branch twice while looking like it covered both
 * (`PaneDirective.kt:31-38`, and `TwoPaneLayoutTest` carries the same note).
 *
 * A pixel read because the claim is a colour, and dark because a border and its part are further
 * apart there than the eye needs - the assertion is equality either way.
 */
@RunWith(AndroidJUnit4::class)
class ShellSidePaneTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun besideTwoPanesTheSideBarDrawsItsOwnBorder() {
    startSidePane(twoPane = true)

    assertEquals(DarkChromePalette.sideBarBorder, trailingColumn())
  }

  @Test
  fun atOnePaneThereIsNoDocumentToSeparateFrom() {
    startSidePane(twoPane = false)

    assertEquals(DarkChromePalette.sideBarBackground, trailingColumn())
  }

  /** The rightmost column, sampled below the panel's title bar so it is the panel's own body. */
  private fun trailingColumn(): Color {
    val pixels = compose.onRoot().captureToImage().toPixelMap()
    return pixels[pixels.width - 1, pixels.height / 2]
  }

  /**
   * The table of contents rather than the file browser: it is the one panel that reads nothing
   * from the container, so the frame is settled the moment it composes.
   */
  private fun startSidePane(twoPane: Boolean) {
    compose.setContent {
      JupyterDroidTheme(darkTheme = true) {
        ShellSidePane(
          showingPanel = ShownPanel.Shell(SidePanel.TABLE_OF_CONTENTS),
          onePanePanel = ShownPanel.Shell(SidePanel.TABLE_OF_CONTENTS),
          twoPane = twoPane,
          onChoosePanel = {},
          appContainer = AppContainer(),
          spacePorts = EnteredSpacePorts(FakeNotebookRepository()),
          outline = OpenDocumentOutline(),
          debugStack = OpenDebugStack(),
          activeCell = OpenNotebookActiveCell(),
          openDocuments = OpenDocuments(),
          openDocument = {},
          openDocumentPath = null,
          openTabs = emptyList(),
          onActivateTab = {},
          onCloseTab = {},
          saveAllEnabled = false,
          onSaveAllTabs = {},
          onOpenSession = {},
          onOpenTerminal = {},
          browseRequest = null,
          onBrowseRequestHandled = {},
          onRevealMatch = {},
          onNewTerminal = {},
          onOpenSettings = {},
          onOpenLauncher = {},
          onBrowsedDirectory = {},
          browsedDirectory = "",
        )
      }
    }
  }
}
