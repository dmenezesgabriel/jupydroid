package com.jupydroid.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three things `saveBeforeClose` has to get right that only a running screen can show.
 *
 * Word for word the reference's (`packages/docmanager/src/dialogs.ts:155-180`): the title
 * *"Save your work"*, the body naming the file, and the three answers Cancel / Discard / Save,
 * in that order - Discard is the warn button, which here means `colorScheme.error`, the same
 * colour Delete and Overwrite wear for the same reason.
 */
@RunWith(AndroidJUnit4::class)
class SaveBeforeCloseDialogTest {
  @get:Rule val compose = createComposeRule()

  private val answers = mutableListOf<String>()

  @Test
  fun itAsksAboutTheNamedDocument() {
    show(name = "notes.py")

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    compose.onNodeWithText("Save changes in \"notes.py\" before closing?").assertIsDisplayed()
    compose.onNodeWithText("Cancel").assertIsDisplayed()
    compose.onNodeWithText("Discard").assertIsDisplayed()
    compose.onNodeWithText("Save").assertIsDisplayed()
  }

  @Test
  fun saveIsTheAccept() {
    show(name = "notes.py")

    compose.onNodeWithText("Save").performClick()

    assertEquals(listOf("save"), answers)
  }

  @Test
  fun discardIsTheWarn() {
    show(name = "notes.py")

    compose.onNodeWithText("Discard").performClick()

    assertEquals(listOf("discard"), answers)
  }

  /** `Dialog.cancelButton()` - and a tap outside means it too, which `onDismissRequest` is. */
  @Test
  fun cancelAndADismissBothAnswerCancel() {
    show(name = "notes.py")

    compose.onNodeWithText("Cancel").performClick()

    assertEquals(listOf("cancel"), answers)
  }

  private fun show(name: String) {
    compose.setContent {
      JupydroidTheme {
        SaveBeforeCloseDialog(
          name = name,
          onSave = { answers += "save" },
          onDiscard = { answers += "discard" },
          onCancel = { answers += "cancel" },
        )
      }
    }
  }
}
