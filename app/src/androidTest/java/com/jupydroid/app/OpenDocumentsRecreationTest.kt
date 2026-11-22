package com.jupydroid.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * What the shell knows about an open document **after the activity is relaunched**, which a
 * rotation does on every turn of the device.
 *
 * Instrumented for [CompositionRootTest]'s reason: what survives a recreation is the framework's
 * to decide. A document's ViewModel survives it and registers with [OpenDocuments] only when it
 * is built, so a registry that did not survive too is empty beside a live, unsaved document - and
 * the close that should have asked simply closes.
 *
 * Autosave is off for `FileEditorCloseGateTest`'s reason: with it on, `dirty` clears itself and
 * the ask is a race rather than a state.
 */
@RunWith(AndroidJUnit4::class)
class OpenDocumentsRecreationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @get:Rule val temp = TemporaryFolder()

  /** One per process, as `JupydroidApplication` builds it - a relaunch gets the same one. */
  private val appContainer by lazy {
    AppContainer(
      launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
      editorSettings = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
      enteredSpacePorts = { _, _ ->
        EnteredSpacePorts(
          notebookRepository = DiskNotebookRepository(temp.root),
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          workspaceFiles = DiskWorkspaceFiles(temp.root),
        )
      },
    )
  }

  /** The control: the ask this test is about exists before any relaunch. */
  @Test
  fun closingAnUnsavedDocumentAsks() {
    openTheFileAndTypeIntoIt()

    compose.onNodeWithContentDescription(CLOSE).performClick()

    compose.onNodeWithText(ASK).assertIsDisplayed()
  }

  @Test
  fun closingAnUnsavedDocumentStillAsksAfterTheActivityIsRelaunched() {
    openTheFileAndTypeIntoIt()

    compose.activityRule.scenario.recreate()
    showTheShell()
    waitForText(TYPED)

    compose.onNodeWithContentDescription(CLOSE).performClick()

    compose.onNodeWithText(ASK).assertIsDisplayed()
  }

  private fun openTheFileAndTypeIntoIt() {
    File(temp.root, FILE).writeText(SEED)
    showTheShell()
    compose.enterBaseSpace()
    compose.onNodeWithText(FILE).performClick()
    waitForText(SEED)
    compose.onNode(hasSetTextAction()).performTextInput("x")
    waitForText(TYPED)
    // Past the 150ms an edit takes to reach the document (`CellSourceTracking.kt`): a close inside
    // it is a clean close, and nothing drawn at one pane says the edit has landed.
    val landed = System.nanoTime() + EDIT_LANDS_NANOS
    compose.waitUntil(TIMEOUT_MILLIS) { System.nanoTime() > landed }
  }

  /** Through the activity rather than the rule: the relaunched activity is a new one to fill. */
  private fun showTheShell() {
    compose.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        JupydroidTheme {
          MainNavigation(
            appContainer = appContainer,
            sharedNotebook = null,
            onImportConsumed = {},
            // Pinned for `NotebookNavigationTest`'s reason: the tablet's own rotation decides
            // which side of the two-pane threshold this is on, and the close has a different name there.
            twoPane = false,
          )
        }
      }
    }
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val FILE = "notes.py"
    const val SEED = "cat"
    const val TYPED = "catx"
    const val CLOSE = "Back to files"
    const val ASK = "Save your work"
    const val TIMEOUT_MILLIS = 5_000L
    const val EDIT_LANDS_NANOS = 1_000_000_000L
  }
}
