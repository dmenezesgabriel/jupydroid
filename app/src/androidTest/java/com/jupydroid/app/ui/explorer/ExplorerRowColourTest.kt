package com.jupydroid.app.ui.explorer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeExplorerSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.theme.ChromePalette
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.textLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * What colour a Files row's two lines are drawn in, read off the layout each `Text` measured.
 *
 * Measured first: in dark, the open document's name and age were both `#8C8C8C` on the row's
 * `#2C2D2E` wash, 4.10:1. The name stated no colour, so it took the one `Scaffold` derives from its
 * container, and `contentColorFor` matches a container to a role by hex - `sideBarBackground` and
 * `surfaceVariant` are both `#191A1B`, which made the name `onSurfaceVariant`, the *secondary* colour.
 * A JVM test cannot see that: the colour is what a composition resolved.
 */
@RunWith(AndroidJUnit4::class)
class ExplorerRowColourTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private lateinit var chrome: ChromePalette

  @Test
  fun theOpenRowsNameTakesTheListsInactiveSelectionForeground() {
    showDarkExplorer(openDocumentPath = NAME)

    assertEquals(chrome.listInactiveSelectionForeground, colourOf(NAME))
  }

  @Test
  fun aRowAtRestNameTakesTheSideBarForeground() {
    showDarkExplorer(openDocumentPath = null)

    assertEquals(chrome.sideBarForeground, colourOf(NAME))
  }

  /** `.label-description { opacity: .7 }` over the row's own foreground, not a colour of its own. */
  @Test
  fun theOpenRowsAgeIsItsForegroundAtTheReferencesDescriptionOpacity() {
    showDarkExplorer(openDocumentPath = NAME)

    assertEquals(chrome.listInactiveSelectionForeground.copy(alpha = DESCRIPTION_ALPHA), colourOf(AGE))
  }

  @Test
  fun aRowAtRestAgeIsTheSideBarForegroundAtTheSameOpacity() {
    showDarkExplorer(openDocumentPath = null)

    assertEquals(chrome.sideBarForeground.copy(alpha = DESCRIPTION_ALPHA), colourOf(AGE))
  }

  private fun colourOf(text: String): Color = compose.onNodeWithText(text, useUnmergedTree = true)
    .textLayout().layoutInput.style.color

  private fun showDarkExplorer(openDocumentPath: String?) {
    File(temp.root, NAME).writeText("x")
    compose.setContent {
      JupydroidTheme(darkTheme = true) {
        ChromeCapture { chrome = it }
        ExplorerScreen(
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          workspaceFiles = DiskWorkspaceFiles(temp.root),
          notebookRepository = DiskNotebookRepository(temp.root),
          kernelSpecs = FakeKernelSpecs(),
          space = BASE_SPACE,
          recentDocuments = FakeRecentDocumentsRepository(emptyList()),
          explorerSettings = FakeExplorerSettingsRepository(),
          onOpenFile = {},
          openDocumentPath = openDocumentPath,
        )
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText(NAME), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Composable
  private fun ChromeCapture(onPalette: (ChromePalette) -> Unit) = onPalette(chromePalette)

  private companion object {
    const val NAME = "notes.txt"
    const val AGE = "just now"
    const val DESCRIPTION_ALPHA = 0.7f
    const val TIMEOUT_MILLIS = 5_000L
  }
}
