package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.FILE_CHANGED_TITLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * **A text file rewritten under its editor: the screen asks, and each answer can be pressed.**
 *
 * Over a real directory, so the refusal is the filesystem's own: the file is rewritten behind the
 * editor, the debounced save finds a file it did not write, and *File Changed* has to be on screen
 * with something to press. Revert is the half no JVM test can answer - the disk's text has to
 * reach the **field**, which sees a changed `FileEditorUiState.text` only through `revision`.
 */
@RunWith(AndroidJUnit4::class)
class FileEditorSaveConflictScreenTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val open = OpenDocuments()

  private val notes get() = File(temp.root, "notes.py")

  @Test
  fun aSaveOverAFileRewrittenBehindTheEditorAsks() {
    showTheFileRewrittenUnderAnEdit()

    compose.onNodeWithText("\"notes.py\" has changed on disk", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Cancel").assertIsDisplayed()
    compose.onNodeWithText("Revert").assertIsDisplayed()
    compose.onNodeWithText("Overwrite").assertIsDisplayed()
    assertEquals(ON_DISK, notes.readText())
  }

  @Test
  fun revertPutsTheDisksTextInTheField() {
    showTheFileRewrittenUnderAnEdit()

    compose.onNodeWithText("Revert").performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText(ON_DISK).assertExists()
    compose.onNodeWithText(FILE_CHANGED_TITLE).assertDoesNotExist()
    assertEquals(ON_DISK, notes.readText())
  }

  @Test
  fun overwritePutsTheEditorsTextOnDisk() {
    showTheFileRewrittenUnderAnEdit()

    compose.onNodeWithText("Overwrite").performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { notes.readText() == "dog = 1" }
    compose.onNodeWithText(FILE_CHANGED_TITLE).assertDoesNotExist()
    compose.onNodeWithText("dog = 1").assertExists()
  }

  /** Opened, rewritten on disk, then edited: waits for the debounced save's refusal to be on screen. */
  private fun showTheFileRewrittenUnderAnEdit() {
    notes.writeText("cat = 1")
    compose.setContent {
      JupydroidTheme {
        FileEditorScreen(
          path = "notes.py",
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          workspaceFiles = DiskWorkspaceFiles(temp.root),
          applicationScope = scope,
          editorSettingsRepository = FakeEditorSettingsRepository(),
          onBack = {},
          openDocuments = open,
        )
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    notes.writeText(ON_DISK)
    compose.runOnUiThread { open.at("notes.py")?.applyEdits(cell = null, edits = listOf(TextEdit(0..2, "dog"))) }
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText(FILE_CHANGED_TITLE)).fetchSemanticsNodes().isNotEmpty()
    }
  }
}

private const val ON_DISK = "changed = 'on disk'"

/** The file has to load, and the save's debounce has to pass, before there is anything to look at. */
private const val TIMEOUT_MILLIS = 5_000L
