package com.jupydroid.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The open document hands its structure to the shell, and takes it back when it closes.
 *
 * The two are different `NavEntry`s - the side panel is in the list pane, the headings are derived
 * in the detail pane's `EditorViewModel` - so [OpenDocumentOutline] is the only thing that crosses
 * between them, and the crossing is what this asserts. The reference does the same from
 * `labShell.currentChanged` and `widget.disposed`
 * (`packages/toc-extension/src/index.ts:316,341-343,353`).
 *
 * Instrumented rather than JVM because `OpenDocumentOutlineTest` already pins the type, and what is
 * left is the wiring: whether a real editor entry, composed and then disposed, publishes and clears.
 */
@RunWith(AndroidJUnit4::class)
class DocumentOutlineNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  private val outline = OpenDocumentOutline()

  @Test
  fun openingANotebookPublishesItsHeadings() {
    startAppWithOneNotebook()

    compose.onNodeWithText(TITLE).performClick()
    waitForText(HEADING_TEXT)

    val published = requireNotNull(outline.current) { "nothing was published for the open notebook" }
    assertEquals(TITLE, published.title)
    assertEquals(listOf(1 to HEADING_TEXT), published.headings.map { it.level to it.text })
  }

  /** **A table of contents cannot outlive its document** - see [OpenDocumentOutline]. */
  @Test
  fun leavingTheNotebookEmptiesTheOutline() {
    startAppWithOneNotebook()
    compose.onNodeWithText(TITLE).performClick()
    waitForText(HEADING_TEXT)

    compose.onNodeWithContentDescription("Back to notebooks").performClick()
    waitForText(WORKSPACE_SECTION)

    assertNull(outline.current)
  }

  private fun startAppWithOneNotebook() {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking { repository.saveNotebook(notebook()) }
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            notebookRepository = repository,
            workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
          ),
          importTitle = null,
          onImportConsumed = {},
          outline = outline,
        )
      }
    }
  }

  private fun notebook() = Notebook(
    TITLE,
    TITLE,
    "python3",
    listOf(Cell("c1", CellKind.MARKDOWN, "# $HEADING_TEXT")),
  )

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val TITLE = "Outlined.ipynb"
    const val HEADING_TEXT = "Chapter one"

    /** The section only the explorer draws - the wait `p2q` established as the honest one. */
    const val WORKSPACE_SECTION = "Workspace"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
