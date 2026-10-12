package com.jupydroid.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What `Context._raiseConflict` has to get right that only a running screen can show
 * (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`): the title *"File Changed"*, a body
 * naming the file, and three answers that can each be pressed - Cancel, Revert, Overwrite.
 */
@RunWith(AndroidJUnit4::class)
class FileChangedDialogTest {
  @get:Rule val compose = createComposeRule()

  private val answers = mutableListOf<String>()

  @Test
  fun itAsksAboutTheNamedDocument() {
    show(name = "Analysis.ipynb")

    compose.onNodeWithText(FILE_CHANGED_TITLE).assertIsDisplayed()
    compose.onNodeWithText("\"Analysis.ipynb\" has changed on disk", substring = true).assertIsDisplayed()
  }

  @Test
  fun overwriteAnswersOverwrite() {
    show(name = "Analysis.ipynb")

    compose.onNodeWithText("Overwrite").performClick()

    assertEquals(listOf("overwrite"), answers)
  }

  @Test
  fun revertAnswersRevert() {
    show(name = "Analysis.ipynb")

    compose.onNodeWithText("Revert").performClick()

    assertEquals(listOf("revert"), answers)
  }

  @Test
  fun cancelAnswersCancel() {
    show(name = "Analysis.ipynb")

    compose.onNodeWithText("Cancel").performClick()

    assertEquals(listOf("cancel"), answers)
  }

  private fun show(name: String) {
    compose.setContent {
      JupyterDroidTheme {
        FileChangedDialog(
          name = name,
          onOverwrite = { answers += "overwrite" },
          onRevert = { answers += "revert" },
          onCancel = { answers += "cancel" },
        )
      }
    }
  }
}
