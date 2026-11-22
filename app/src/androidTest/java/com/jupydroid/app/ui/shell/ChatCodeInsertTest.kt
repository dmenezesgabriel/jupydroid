package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.jupydroid.app.RecordingNotebookCells
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.extensions.ai.data.fake.FakeAgentChat
import com.jupydroid.extensions.ai.domain.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Insert under a code block of an agent's answer hands its code to the open notebook's active cell, as
 * jupyter-chat's toolbar does through its active-cell manager (jupyter-chat@2c224bc
 * `packages/jupyter-chat/src/components/code-blocks/code-toolbar.tsx`, `src/active-cell-manager.ts`).
 */
class ChatCodeInsertTest {
  @get:Rule
  val compose = createComposeRule()

  private val panel = UntitledChatPanel(compose)

  @Test
  fun insertUnderACodeBlockHandsItsCodeToTheActiveCell() {
    val notebookCells = showOver(RecordingNotebookCells(available = true))

    compose.onNodeWithContentDescription(INSERT_BELOW).performClick()
    compose.waitForIdle()

    assertEquals(listOf("x = 1"), notebookCells.inserted)
  }

  /** jupyter-chat disables its inserts with "(no active cell)" in the tooltip (`code-toolbar.tsx`). */
  @Test
  fun insertIsDisabledWithNoActiveCell() {
    showOver(RecordingNotebookCells(available = false))

    compose.onNodeWithContentDescription("$INSERT_BELOW (no active cell)").assertIsNotEnabled()
  }

  /** jupyter-chat names each icon in a tooltip on hover; a touchscreen has none, so a long press shows it. */
  @Test
  fun aLongPressOnTheIconNamesIt() {
    showOver(RecordingNotebookCells(available = true))

    compose.onNodeWithContentDescription(INSERT_BELOW).performTouchInput { longClick() }

    compose.onNodeWithText(INSERT_BELOW).assertIsDisplayed()
  }

  /** jupyter-chat's toolbar puts its inserts before its copy (`code-toolbar.tsx`). */
  @Test
  fun insertSitsLeftOfCopyOnItsRow() {
    showOver(RecordingNotebookCells(available = true))

    val insert = compose.onNodeWithContentDescription(INSERT_BELOW).getBoundsInRoot()
    val copy = compose.onNodeWithContentDescription("Copy").getBoundsInRoot()
    assertTrue("Insert at $insert is not left of Copy at $copy", insert.right <= copy.left)
    assertEquals("Insert and Copy are not on one row", insert.top, copy.top)
  }

  /** The panel open on an answer whose one code block is `x = 1`, over [notebookCells]. */
  private fun showOver(notebookCells: RecordingNotebookCells): RecordingNotebookCells {
    val answer = FakeAgentChat(history = listOf(ChatMessage(id = "a", body = ANSWER, fromMe = false)))
    compose.setContent {
      JupydroidTheme {
        ContributedSidePanel(panel.contribution(answer), UntitledChatPanel.HOST.copy(notebookCells = notebookCells))
      }
    }
    panel.open()
    return notebookCells
  }

  private companion object {
    const val ANSWER = "## Result\n\n```python\nx = 1\n```"

    /** jupyter-chat's own title for it (`code-toolbar.tsx`). */
    const val INSERT_BELOW = "Insert below active cell"
  }
}
