package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.UNSAVED_CHANGES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The `•` beside a dirty document's name - JupyterLab's own answer is the busy dot that takes the
 * tab's close icon over while `jp-mod-dirty` is on (`ui-components/style/icons.css:268-278`),
 * and this editor's title bar is where that label lives here. The marker tracks `dirty` itself,
 * so a landed save takes it away again - `context.dirty` clears when the write resolves, not when
 * the edit happens.
 *
 * Autosave is seeded **off** as in `FileEditorCloseGateTest`: with it on the debounce clears
 * `dirty` inside 500ms and the marker would be a race, not a state.
 */
@RunWith(AndroidJUnit4::class)
class FileEditorDirtyMarkerTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var editor: FileEditorViewModel

  @Test
  fun aCleanDocumentCarriesNoMarker() {
    show()

    compose.onNodeWithContentDescription(UNSAVED_CHANGES).assertDoesNotExist()
  }

  @Test
  fun anEditMarksTheTitle() {
    show()

    type()

    compose.onNodeWithContentDescription(UNSAVED_CHANGES).assertIsDisplayed()
  }

  @Test
  fun aLandedSaveTakesTheMarkerAway() {
    show()
    type()

    editor.saveNow {}

    compose.waitUntil(LOAD_TIMEOUT_MILLIS) { !editor.uiState.value.dirty }
    compose.onNodeWithContentDescription(UNSAVED_CHANGES).assertDoesNotExist()
  }

  private fun type() {
    compose.onNode(hasSetTextAction()).performTextInput("x")
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) { editor.uiState.value.dirty }
  }

  private fun show() {
    File(temp.root, "notes.py").writeText("cat")
    editor =
      FileEditorViewModel(
        "notes.py",
        DiskWorkspaceRepository(temp.root),
        scope,
        editorSettings = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
      )
    compose.setContent {
      JupyterDroidTheme {
        FileEditorScreen(
          path = "notes.py",
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          applicationScope = scope,
          editorSettingsRepository = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
          onBack = {},
          viewModel = editor,
        )
      }
    }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
    }
  }
}

/** Long enough for a file read off disk, short enough to fail - `FileEditorRevealTest`'s own. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
