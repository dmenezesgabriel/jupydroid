package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notebook's title is where its rename lives, which is where JupyterLab puts it -
 * `docmanager:rename` hangs off `[data-type="document-title"]`
 * (`packages/docmanager-extension/schema/plugin.json:73-77`).
 *
 * Instrumented because "is there something here to press" is not a question a JVM test can
 * answer, which is the whole reason `CellHeaderTest` exists.
 */
@RunWith(AndroidJUnit4::class)
class EditorTopBarTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theTitleIsOnScreen() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").assertIsDisplayed()
  }

  @Test
  fun tappingTheTitleOpensTheRenameDialog() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()

    compose.onNodeWithText("Rename notebook").assertIsDisplayed()
  }

  @Test
  fun renamingFromTheTitleReportsTheNewTitle() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Linear algebra")
    compose.onNodeWithText("Rename").performClick()

    assertEquals(listOf("Linear algebra"), renamed)
  }

  @Test
  fun cancellingRenamesNothing() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Linear algebra")
    compose.onNodeWithText("Cancel").performClick()

    assertEquals(emptyList<String>(), renamed)
  }

  /** Run All stays reachable: adding a rename target to the bar must not cost the action on it. */
  @Test
  fun runAllIsStillThere() {
    showTopBar()

    compose.onNodeWithContentDescription("Run all cells").assertIsDisplayed()
  }

  private val renamed = mutableListOf<String>()

  private fun showTopBar() {
    compose.setContent {
      JupyterDroidTheme {
        EditorTopBar(
          title = "Intro to NumPy",
          busy = false,
          onRunAll = {},
          onRename = { renamed += it },
          onBack = {},
        )
      }
    }
  }

}
