package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * **A replacement pushed into the open file editor shows in its field.**
 *
 * The half no JVM test can answer, and it is the reason `FileEditorUiState.revision` exists:
 * `rememberTextFieldState(initialText)` is `rememberSaveable(saver) { … }` with **no keys**
 * (foundation `TextFieldState.kt:657-661`), so a changed `text` on its own leaves the field drawing
 * what it already had. A ViewModel test would have gone green against a screen showing the old
 * word - which is exactly the class of defect the search work kept finding only in a picture.
 *
 * This is what `BulkTextEdits` buys by resolving a model for every resource rather than writing the
 * file (`bulkTextEdits.ts:227`): the reader watches the replacement land.
 */
@RunWith(AndroidJUnit4::class)
class FileEditorAdoptsAReplacementTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val open = OpenDocuments()

  private fun showTheFile() {
    File(temp.root, "notes.py").writeText("cat = 1")
    compose.setContent {
      JupyterDroidTheme {
        FileEditorScreen(
          path = "notes.py",
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          applicationScope = scope,
          editorSettingsRepository = FakeEditorSettingsRepository(),
          onBack = {},
          openDocuments = open,
        )
      }
    }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun theFieldShowsAReplacementItDidNotType() {
    showTheFile()
    compose.onNodeWithText("cat = 1").assertExists()

    compose.runOnUiThread { open.at("notes.py")?.applyEdits(cell = null, edits = listOf(TextEdit(0..2, "dog"))) }
    compose.waitForIdle()

    compose.onNodeWithText("dog = 1").assertExists()
  }

  /**
   * And the document is reachable at all, which is the registry's own half: the screen composed is
   * what put it there, with nothing but a path to ask by.
   */
  @Test
  fun theScreenIsWhatPutsTheDocumentInTheRegistry() {
    showTheFile()

    compose.runOnUiThread { requireNotNull(open.at("notes.py")) }
  }
}

/** The file has to arrive before there is a field to look at. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
