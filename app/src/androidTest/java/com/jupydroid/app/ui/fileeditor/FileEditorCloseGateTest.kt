package com.jupydroid.app.ui.fileeditor

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Explorer
import com.jupydroid.app.FileEditor
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.orderedAsOpened
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.shell.TabbedDocumentPane
import com.jupydroid.app.ui.shell.rememberMainAreaCloseGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

/**
 * The `saveBeforeClose` gate on the file editor's own ways out: the tab strip's close (✕ or the
 * one-pane arrow - one act) and system Back. A dirty document asks first
 * (`docmanager/src/widgetmanager.ts:342-388`), a clean one just leaves, and a save that cannot be
 * made keeps the document open - `onClose` awaits `context.save()` and a rejection is a close
 * that never happens.
 *
 * Drawn under the real `TabbedDocumentPane` for `EditorCloseGateTest`'s own reason: the close
 * affordance lives on the strip now, and a bare screen has nothing to tap.
 *
 * Autosave is seeded **off** for every case, the way the p6c-8 Maestro flow seeds it: with it on
 * the debounce clears `dirty` in 500ms and the gate would be a race, not a state.
 */
@RunWith(AndroidJUnit4::class)
class FileEditorCloseGateTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id), FileEditor("notes.py"))
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()
  private val liveTitles = mutableStateMapOf<NavKey, String>()
  private lateinit var editor: FileEditorViewModel

  /** A tree that lost the directory under it - `FileEditorSaveFailureTest`'s own fake, mirrored. */
  private class VanishedWorkspaceRepository(
    private val stillReadable: WorkspaceRepository =
      FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "cat")),
  ) : WorkspaceRepository by stillReadable {
    override suspend fun writeText(path: String, text: String, expected: FileStamp?): FileStamp =
      throw FileNotFoundException("$path: open failed: ENOENT (No such file or directory)")
  }

  @Test
  fun aCleanDocumentClosesWithoutAsking() {
    show()

    compose.onNodeWithContentDescription("Back to files").performClick()

    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id)), backStack.toList())
  }

  @Test
  fun dismissOnADirtyDocumentAsksInstead() {
    show()
    type()

    compose.onNodeWithContentDescription("Back to files").performClick()

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    assertEquals(FileEditor("notes.py"), backStack.last())
  }

  @Test
  fun systemBackOnADirtyDocumentAsksToo() {
    show()
    type()

    Espresso.pressBack()

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    assertEquals(FileEditor("notes.py"), backStack.last())
  }

  @Test
  fun cancelKeepsTheDocumentOpen() {
    show()
    type()
    compose.onNodeWithContentDescription("Back to files").performClick()

    compose.onNodeWithText("Cancel").performClick()

    compose.onNodeWithText("Save your work").assertDoesNotExist()
    assertEquals(FileEditor("notes.py"), backStack.last())
  }

  /**
   * Discard closes and writes nothing - `ignoreSave` takes the document away as it stands on
   * disk, which is why the seed must still be the whole file afterwards.
   */
  @Test
  fun discardLeavesDiskUntouched() {
    show()
    type()
    compose.onNodeWithContentDescription("Back to files").performClick()

    compose.onNodeWithText("Discard").performClick()

    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id)), backStack.toList())
    assertEquals("cat", File(temp.root, "notes.py").readText())
  }

  /**
   * Save writes first and only then closes - the reference's own order (`await context.save()`
   * before `widget.dispose()`), so the file on disk already holds the edit when `onBack` runs.
   */
  @Test
  fun saveWritesThenCloses() {
    show()
    type()
    compose.onNodeWithContentDescription("Back to files").performClick()

    compose.onNodeWithText("Save").performClick()
    compose.waitForIdle()

    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id)), backStack.toList())
    assertEquals("catx", File(temp.root, "notes.py").readText())
  }

  /** `context.save()` rejecting propagates out of `onClose` - the widget is never disposed. */
  @Test
  fun aSaveThatCannotBeMadeKeepsTheDocumentOpen() {
    show(repository = VanishedWorkspaceRepository())
    type()
    compose.onNodeWithContentDescription("Back to files").performClick()

    compose.onNodeWithText("Save").performClick()
    compose.waitForIdle()

    assertEquals(FileEditor("notes.py"), backStack.last())
  }

  /**
   * Types through the field and waits for the edit to actually reach the ViewModel:
   * `reportCellEdits` debounces 150ms (`CellSourceTracking.kt`), so clicking the dismiss in the
   * same tick would test a clean close, not the gate.
   */
  private fun type() {
    compose.onNode(hasSetTextAction()).performTextInput("x")
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) { editor.uiState.value.dirty }
  }

  private fun show(repository: WorkspaceRepository = DiskWorkspaceRepository(temp.root)) {
    File(temp.root, "notes.py").writeText("cat")
    editor =
      FileEditorViewModel(
        "notes.py",
        repository,
        scope,
        open,
        editorSettings = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
      )
    compose.setContent {
      JupyterDroidTheme {
        val order = remember { mutableStateListOf<NavKey>() }
        val tabs = backStack.mainAreaTabs().orderedAsOpened(order)
        LaunchedEffect(tabs) {
          order.clear()
          order.addAll(tabs)
        }
        val closeTab = rememberMainAreaCloseGate(backStack, tabs, open, paths)
        TabbedDocumentPane(
          backStack, open, paths, tabs, liveTitles, repository, FakeWorkspaceFiles(), twoPane = false, closeTab,
          onOpenDocument = {},
          documentCheckpoints = FakeDocumentCheckpoints(repository),
        ) {
          FileEditorScreen(
            path = "notes.py",
            workspaceRepository = repository,
            applicationScope = scope,
            editorSettingsRepository = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
            onBack = { backStack.removeLastOrNull() },
            viewModel = editor,
          )
        }
      }
    }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
    }
  }
}

/** Long enough for a file read off disk, short enough to fail - `FileEditorRevealTest`'s own. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
