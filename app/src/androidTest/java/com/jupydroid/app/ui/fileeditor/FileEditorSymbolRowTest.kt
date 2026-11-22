package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.editor.SYMBOL_ROW_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The file editor's symbol row: there while the file has the caret, and typing what the keyboard
 * would type into this kind of file.
 *
 * Composed with no `WindowKeyRowHosted` above it, so the row is drawn inside the screen
 * (`WindowKeyRowTest.withoutAHostTheRowIsDrawnWhereItWasPublished`).
 */
@RunWith(AndroidJUnit4::class)
class FileEditorSymbolRowTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Test
  fun theRowIsAbsentUntilTheFileHasTheCaret() {
    open("analysis.py")

    compose.onNodeWithTag(SYMBOL_ROW_TAG).assertDoesNotExist()
  }

  @Test
  fun aKeyFromTheRowTypesIntoTheFile() {
    open("analysis.py")
    compose.onNode(hasSetTextAction()).performClick()

    compose.onNodeWithText(":").performScrollTo().performClick()

    compose.onNode(hasSetTextAction()).assert(hasText(":"))
  }

  /** In prose the keyboard's `(` stays alone (`typingRulesFor`), so the row's does too. */
  @Test
  fun aBracketFromTheRowStaysAloneInProse() {
    open("notes.md")
    compose.onNode(hasSetTextAction()).performClick()

    compose.onNodeWithText("(").performScrollTo().performClick()

    compose.onNode(hasSetTextAction()).assert(hasText("("))
  }

  @Test
  fun theSettingOffKeepsTheRowAway() {
    open("analysis.py", EditorSettings(symbolRowShown = false))
    compose.onNode(hasSetTextAction()).performClick()

    compose.onNodeWithTag(SYMBOL_ROW_TAG).assertDoesNotExist()
  }

  private fun open(path: String, settings: EditorSettings = EditorSettings()) {
    File(temp.root, path).writeText("")
    compose.setContent {
      JupydroidTheme {
        FileEditorScreen(
          path = path,
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          workspaceFiles = DiskWorkspaceFiles(temp.root),
          applicationScope = scope,
          editorSettingsRepository = FakeEditorSettingsRepository(settings),
          onBack = {},
        )
      }
    }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
    }
  }
}

/** Long enough for a file read off disk, short enough to fail. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
