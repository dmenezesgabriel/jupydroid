package com.jupydroid.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.console.CONSOLE_INPUT_FIELD_TAG
import com.jupydroid.app.ui.editor.NEW_CONSOLE_FOR_NOTEBOOK
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import com.jupydroid.app.ui.shell.NEW_LAUNCHER
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * `notebook:create-console`, end to end: a real tap sequence from the notebook editor's own Kernel
 * menu through to a Console running against `FakeKernelGateway`, and Back returning to the
 * notebook it was opened from rather than to the browser - P6b-0's own fix, exercised for real
 * rather than only by `NotebookBackStackTest`'s plain-list cases.
 *
 * Instrumented rather than JVM, `NotebookNavigationTest`'s own reason: what is on screen after a
 * pop is not a question a JVM test can answer. `FakeKernelGateway`, not a live kernel - this proves
 * the navigation wiring; the live-kernel flow on the physical tablet is its own step.
 */
@RunWith(AndroidJUnit4::class)
class ConsoleNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun openingAConsoleFromTheKernelMenuSharesTheNotebooksKernel() {
    startAppWithOneNotebook()
    openTheNotebook()

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(NEW_CONSOLE_FOR_NOTEBOOK).performClick()

    waitForTag(CONSOLE_INPUT_FIELD_TAG)
    // The title moved to the tab with P8e - the console's tab carries the notebook's name
    // (`title.label`'s own home, `widget.ts`'s `title.label = name`), and the notebook's own tab
    // has the same title, so the selected one is the console's.
    compose.onNode(isSelected() and hasContentDescription("Tab: $NOTEBOOK_TITLE")).assertIsDisplayed()

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()

    waitForText("1 + 1")

    // Real evidence of the whole path, not just the standalone screen P6b-9a already captured:
    // this Console was reached by an actual tap sequence from an open notebook.
    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.getExternalFilesDir(null), EVIDENCE_FILE_NAME)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out) }
    assertEquals("expected a non-empty screenshot at ${file.absolutePath}", true, file.length() > 0)
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.executeShellCommand("cp ${file.absolutePath} /sdcard/$EVIDENCE_FILE_NAME").use {
      it.fileDescriptor.let { fd -> java.io.FileInputStream(fd).use { stream -> stream.readBytes() } }
    }
  }

  /** Back returns to the notebook the Console was opened from, not to the browser - P6b-0's fix. */
  @Test
  fun backFromAConsoleReturnsToItsOwnNotebookThenToTheBrowser() {
    startAppWithOneNotebook()
    openTheNotebook()

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(NEW_CONSOLE_FOR_NOTEBOOK).performClick()
    waitForTag(CONSOLE_INPUT_FIELD_TAG)

    // The strip's arrow names the entry actually beneath: this Console sits over its notebook,
    // so the destination is the document's own name rather than a canned "notebook".
    compose.onNodeWithContentDescription("Back to $NOTEBOOK_TITLE").performClick()
    waitForText(NOTEBOOK_SOURCE)
    compose.onNodeWithText(NOTEBOOK_SOURCE).assertIsDisplayed()

    compose.onNodeWithContentDescription("Back to files").performClick()
    waitForText(NOTEBOOK_TITLE)
    listingRow(NOTEBOOK_TITLE).assertIsDisplayed()
  }

  /**
   * `console:create` from the Launcher - a standalone Console titled `Console 1` on its own minted
   * session (P6d-5). It replaces the detail rather than stacking on it, so Back leaves the shell
   * on the browser and the dismiss label says files, not a notebook it is not attached to. The
   * gateway is the fake's echo; the real kernel is the tablet Maestro flow's own evidence.
   */
  @Test
  fun theLaunchersConsoleCardOpensAStandaloneConsole() {
    startAppWithOneNotebook()

    openLauncherConsoleCard()
    waitForTag(CONSOLE_INPUT_FIELD_TAG)
    // The name is the tab's now (`title.label`), not a row in the panel.
    compose.onNodeWithContentDescription("Tab: Console 1").assertIsDisplayed()

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()
    waitForText("1 + 1")

    compose.onNodeWithContentDescription("Back to files").performClick()
    waitForText(NOTEBOOK_TITLE)
    listingRow(NOTEBOOK_TITLE).assertIsDisplayed()
  }

  /**
   * One console at a time - the single `consoleSession` holder's rule: a second card mints
   * `Console 2` and sweeps the first, leaving no dead entry composing against the newer session.
   * Two panes, because the rail's `+` is the reach point that exists while a document is open -
   * at one pane the bar is the browser's own and the launcher cannot be reached until Back.
   */
  @Test
  fun aSecondConsoleCardSweepsTheFirstStandalone() {
    startAppWithOneNotebook(twoPane = true)

    openLauncherConsoleCard()
    waitForTag(CONSOLE_INPUT_FIELD_TAG)
    compose.onNodeWithContentDescription("Tab: Console 1").assertIsDisplayed()

    compose.onNodeWithContentDescription(NEW_LAUNCHER).performClick()
    waitForTag("launcher-screen")
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_CONSOLE.name}").performClick()

    waitForContentDescription("Tab: Console 2")
    compose.onAllNodesWithContentDescription("Tab: Console 1").assertCountEquals(0)
  }

  private fun openLauncherConsoleCard() {
    compose.onNodeWithContentDescription(NEW_LAUNCHER).performClick()
    waitForTag("launcher-screen")
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_CONSOLE.name}").performClick()
  }

  private fun openTheNotebook() {
    listingRow(NOTEBOOK_TITLE).performClick()
    waitForText(NOTEBOOK_SOURCE)
  }

  private fun listingRow(title: String) = compose.onNode(hasText(title) and hasContentDescription("File"))

  private fun startAppWithOneNotebook(twoPane: Boolean = false) {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      repository.saveNotebook(
        Notebook(NOTEBOOK_TITLE, NOTEBOOK_TITLE, "python3", listOf(Cell("c1", CellKind.CODE, NOTEBOOK_SOURCE))),
      )
    }
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
          // Pinned rather than measured: `currentWindowAdaptiveInfo()` reads the real window, which
          // this physical tablet's own current rotation can put on either side of the two-pane
          // threshold - `EditorScreen`'s own `CellActionsTest` citation is why `MainNavigation`
          // takes this as a parameter at all. The tests default to one pane, whose strip arrow is
          // what they assert on ("Back to <destination>"); the sweep case wants the two-pane rail's
          // reach instead.
          twoPane = twoPane,
        )
      }
    }
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForContentDescription(description: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForTag(tag: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val NOTEBOOK_TITLE = "Analysis.ipynb"
    const val NOTEBOOK_SOURCE = "print('hi')"
    const val TIMEOUT_MILLIS = 5_000L
    const val PNG_QUALITY = 100
    const val EVIDENCE_FILE_NAME = "p6b-9c-console-navigation.png"
  }
}
