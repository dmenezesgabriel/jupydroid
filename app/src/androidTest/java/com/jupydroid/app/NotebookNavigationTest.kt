package com.jupydroid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Opening one notebook, going back, and opening another must show the second one.
 *
 * It did not. `NavDisplay` is given no `entryDecorators`, and its default is the saveable-state
 * one alone, so `LocalViewModelStoreOwner` inside an entry was the Activity - and
 * `viewModel { … }` with no key resolves on the class name, returning the instance built for the
 * first notebook and dropping the second one's id on the floor. The editor loads once, in `init`,
 * so it had no way to notice.
 *
 * Every reference implementation keys this by document identity rather than by type: JupyterLab's
 * `_findContext` keys on `(normalize(path), factoryName)`
 * (`packages/docmanager/src/manager.ts:545-558`), and IntelliJ hangs its notebook state off the
 * open editor with `EDITOR_NOTEBOOK_KEY`.
 *
 * Instrumented rather than JVM because what is on screen after a pop is not a question a JVM test
 * can answer.
 */
@RunWith(AndroidJUnit4::class)
class NotebookNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * Asserts the second notebook's **cell**, not only its title. A title alone cannot tell a stale
   * ViewModel from a mis-routed id, and the cells are what an edit would be written into.
   */
  @Test
  fun openingASecondNotebookShowsTheSecondNotebook() {
    startAppWithTwoNotebooks()

    compose.onNodeWithText(ALPHA_TITLE).performClick()
    waitForText(ALPHA_SOURCE)
    // The strip's one-pane arrow - the browser is the entry beneath this notebook.
    compose.onNodeWithContentDescription("Back to files").performClick()
    waitForText(BETA_TITLE)

    compose.onNodeWithText(BETA_TITLE).performClick()

    waitForText(BETA_SOURCE)
    compose.onNodeWithText(BETA_SOURCE).assertIsDisplayed()
    assertGone(ALPHA_SOURCE)
  }

  /**
   * A double tap must not push the same notebook twice.
   *
   * Both nav3 decorators key on `NavEntry.contentKey`, so two entries for one notebook would
   * share a `ViewModelStore` *and* a `SaveableStateHolder` slot, and popping the first would
   * clear state the second still needs. JupyterLab answers this with `openOrReveal`, which
   * reveals the widget already open rather than opening a second
   * (`packages/docmanager/src/manager.ts:481-503`).
   *
   * **The clock has to be frozen or this tests nothing.** Written as two plain `performClick`s
   * it passed against no guard at all: the composition settles between them, Recents is gone by
   * the second, and the title it matched was the *editor's* app bar. Holding the clock keeps
   * Recents on screen for both taps, which is the real gesture - a second tap landing during the
   * transition, the same window that made `a2-empty-notebook` flake once in thirty runs.
   */
  @Test
  fun tappingTheSameNotebookTwiceOpensOneEditor() {
    startAppWithTwoNotebooks()

    compose.mainClock.autoAdvance = false
    listingRow(ALPHA_TITLE).performClick()
    // The *listing* row both times. The first tap makes the notebook a recent, so its
    // name is on screen twice at the root - once in the Recents section, once in the listing - and
    // a bare text match finds two nodes. Which row the second tap lands on does not matter to what
    // this asserts; naming one does.
    listingRow(ALPHA_TITLE).performClick()
    compose.mainClock.autoAdvance = true
    waitForText(ALPHA_SOURCE)

    // The strip's one-pane arrow, named for what sits beneath the notebook - the browser.
    compose.onNodeWithContentDescription("Back to files").performClick()

    // One Back is enough only if one entry was pushed; a second editor would still be on screen.
    waitForText(BETA_TITLE)
    compose.onNodeWithText(BETA_TITLE).assertIsDisplayed()
  }

  /**
   * The row in the workspace listing, told from its twin in the Recents section by the icon each
   * one carries - "File" against "Recent file", which is also what a screen reader hears.
   */
  private fun listingRow(title: String) = compose.onNode(hasText(title) and hasContentDescription("File"))

  private fun startAppWithTwoNotebooks() {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      // The id *is* the file name - suffix included, `Contents.IModel.path` - so these two
      // cannot differ, and the row shows exactly what the file is called. They used to differ,
      // and Home then listed "alpha" while the test looked for "Alpha notebook".
      repository.saveNotebook(notebook(ALPHA_TITLE, ALPHA_SOURCE))
      repository.saveNotebook(notebook(BETA_TITLE, BETA_SOURCE))
    }
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            notebookRepository = repository,
            // The list is the explorer, and it lists the *workspace* - the default fake
            // is empty, so without this there are no rows to tap and the test proves nothing.
            workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
          ),
          importTitle = null,
          onImportConsumed = {},
          // Pinned rather than measured: `currentWindowAdaptiveInfo()` reads the real window, and
          // a physical tablet's own current rotation can put this on either side of the two-pane
          // threshold, where the strip's `"Back to files"` arrow disappears instead
          // (`MainAreaTabStrip`).
          // Found live: this flow failed on `SM-X216B` in landscape until pinned - `ConsoleNavigationTest`'s
          // own fix for the identical cause.
          twoPane = false,
        )
      }
    }
    compose.enterBaseSpace()
  }

  private fun notebook(name: String, source: String) =
    Notebook(name, name, "python3", listOf(Cell("$name-c1", CellKind.CODE, source)))

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun assertGone(text: String) {
    val found = compose.onAllNodesWithText(text).fetchSemanticsNodes()
    assert(found.isEmpty()) { "expected '$text' to be gone, found ${found.size} node(s)" }
  }

  private companion object {
    const val ALPHA_TITLE = "Alpha notebook.ipynb"
    const val BETA_TITLE = "Beta notebook.ipynb"

    // Disjoint on purpose: neither string can be matched by the other notebook's cell.
    const val ALPHA_SOURCE = "print('first one only')"
    const val BETA_SOURCE = "print('second one only')"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
