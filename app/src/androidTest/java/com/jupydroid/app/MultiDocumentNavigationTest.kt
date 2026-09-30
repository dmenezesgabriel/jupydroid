package com.jupydroid.app

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Two notebooks open at once hold two live kernel sessions - the half of a stacking main area
 * that the unit tests cannot see.
 *
 * JupyterLab's main area keeps every widget alive beneath the current tab; here a `NavEntry` keeps
 * its `ViewModelStore`, and the ViewModel is what holds the kernel connection - so
 * `[Explorer, Editor(a), Editor(b)]` is two sessions, not one session and a corpse. The fakes stand
 * in for the kernel itself (the real two-session proof on a Space is the Maestro half of the
 * evidence); what they prove is the *wiring*: both ViewModels survive, both connected, and reveal
 * reuses the session it had rather than minting another.
 *
 * Two panes, because the browser has to stay visible to open the second notebook without closing
 * the first - at one pane there is no side area to tap a second row in (the tab strip is the
 * other reach point).
 */
@RunWith(AndroidJUnit4::class)
class MultiDocumentNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  private val open = OpenDocuments()
  private val gateway = CountingKernelGateway()

  @Test
  fun aSecondNotebookKeepsTheFirstsKernelSessionAlive() {
    startAppWithTwoNotebooks()

    listingRow(ALPHA_TITLE).performClick()
    waitForText(ALPHA_SOURCE)
    runFirstCell(sessionsSoFar = 0)

    listingRow(BETA_TITLE).performClick()
    waitForText(BETA_SOURCE)
    runFirstCell(sessionsSoFar = 1)

    // Both documents are still open, and each ran against a session of its own.
    assertEquals(setOf(ALPHA_TITLE, BETA_TITLE), gateway.connectedPaths.toSet())
    assertEquals("opening the second must not shut down the first", 0, gateway.shutDownSessions.size)
    assertNotNull(open.at(ALPHA_TITLE))
    assertNotNull(open.at(BETA_TITLE))
  }

  /**
   * Reveal reuses the session it already had: tapping the first notebook's row again brings its
   * entry - ViewModel, kernel socket and all - forward without a third `connect`. `openOrReveal`'s
   * activate half, and the thing a fresh entry could not fake.
   */
  @Test
  fun reopeningTheFirstNotebookRevealsItWithItsSessionIntact() {
    startAppWithTwoNotebooks()

    listingRow(ALPHA_TITLE).performClick()
    waitForText(ALPHA_SOURCE)
    runFirstCell(sessionsSoFar = 0)
    listingRow(BETA_TITLE).performClick()
    waitForText(BETA_SOURCE)

    listingRow(ALPHA_TITLE).performClick()
    waitForText(ALPHA_SOURCE)

    assertEquals("reveal must not reconnect", 1, gateway.connectedPaths.count { it == ALPHA_TITLE })
  }

  /**
   * Runs the one cell each fixture notebook has, and waits for the kernel session it opens - the
   * echoed output cannot be told from the cell's own source, so the wait is on `connect` itself.
   */
  private fun runFirstCell(sessionsSoFar: Int) {
    compose.onAllNodesWithContentDescription("Run cell")[0].performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { gateway.connectedPaths.size > sessionsSoFar }
  }

  private fun listingRow(title: String) = compose.onNode(hasText(title) and hasContentDescription("File"))

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  /**
   * Every `connect` recorded with the document it was asked for - the session-per-editor proof.
   * `FakeKernelGateway` is final, so this wraps it the way `EditorTestFixtures`' own gateway does.
   */
  private class CountingKernelGateway(private val delegate: FakeKernelGateway = FakeKernelGateway()) :
    KernelGateway by delegate {
    val connectedPaths = mutableListOf<String>()
    val shutDownSessions get() = delegate.shutDownSessions

    override suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice?): KernelSession {
      connectedPaths += documentPath
      return delegate.connect(space, documentPath, kernel)
    }
  }

  private fun startAppWithTwoNotebooks() {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      repository.saveNotebook(notebook(ALPHA_TITLE, ALPHA_SOURCE))
      repository.saveNotebook(notebook(BETA_TITLE, BETA_SOURCE))
    }
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            notebookRepository = repository,
            workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
            kernelGateway = gateway,
          ),
          importTitle = null,
          onImportConsumed = {},
          openDocuments = open,
          // Two panes on purpose: the list has to stay beside the editor for the second tap to
          // stack a document instead of leaving the first behind on Back.
          twoPane = true,
        )
      }
    }
  }

  private fun notebook(name: String, source: String) =
    Notebook(name, name, "python3", listOf(Cell("$name-c1", CellKind.CODE, source)))

  private companion object {
    const val ALPHA_TITLE = "Alpha notebook.ipynb"
    const val BETA_TITLE = "Beta notebook.ipynb"
    const val ALPHA_SOURCE = "print('first one only')"
    const val BETA_SOURCE = "print('second one only')"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
