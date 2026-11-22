package com.jupydroid.app.ui.console

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.editor.RECALL_NEXT_ENTRY
import com.jupydroid.app.ui.editor.RECALL_PREVIOUS_ENTRY
import com.jupydroid.app.ui.editor.SYMBOL_ROW_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * The Console's input row. `ConsoleViewModelTest` already covers what `submit()` does;
 * this is only whether a tap actually reaches it and whether Run is offered while running.
 */
@RunWith(AndroidJUnit4::class)
class ConsoleInputRowTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun typingAndTappingRunInvokesOnSubmit() {
    var submitted = false
    compose.setContent {
      JupydroidTheme {
        ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = { submitted = true })
      }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()

    assertTrue(submitted)
  }

  /**
   * `console:linebreak`'s equivalent. `PythonTypingRulesTest` already covers what a typed
   * `\n` does to the buffer (carries and extends indent) - this is only the one thing that test
   * altitude cannot see, that the same keystroke never reaches [onSubmit].
   */
  @Test
  fun typingANewlineDoesNotInvokeOnSubmit() {
    var submitted = false
    compose.setContent {
      JupydroidTheme {
        ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = { submitted = true })
      }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("if True:\n")

    assertFalse(submitted)
  }

  /** A screen reader calls the field by what it is for; a bare edit box says nothing about the Console. */
  @Test
  fun theFieldIsNamedForTheConsole() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}) }
    }

    compose.onNodeWithContentDescription(CONSOLE_INPUT_NAME).assertIsDisplayed()
  }

  /** `console:history-back`'s mobile stand-in - see `RECALL_PREVIOUS_ENTRY`. */
  @Test
  fun tappingRecallPreviousInvokesOnRecallPreviousEntry() {
    var recalled = false
    compose.setContent {
      JupydroidTheme {
        ConsoleInputRow(
          state = TextFieldState(),
          running = false,
          onSubmit = {},
          onRecallPreviousEntry = { recalled = true },
        )
      }
    }

    compose.onNodeWithContentDescription(RECALL_PREVIOUS_ENTRY).performClick()

    assertTrue(recalled)
  }

  @Test
  fun tappingRecallNextInvokesOnRecallNextEntry() {
    var recalled = false
    compose.setContent {
      JupydroidTheme {
        ConsoleInputRow(
          state = TextFieldState(),
          running = false,
          onSubmit = {},
          onRecallNextEntry = { recalled = true },
        )
      }
    }

    compose.onNodeWithContentDescription(RECALL_NEXT_ENTRY).performClick()

    assertTrue(recalled)
  }

  /** Replaced rather than appended - `RawCellFieldTest.acceptsTypingAndReportsWhatWasTyped`'s own reason. */
  @Test
  fun typingReportsTheEditedSourceOnADebounce() {
    var reported: String? = null
    compose.setContent {
      JupydroidTheme {
        ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}, onSourceEdited = { reported = it })
      }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")

    compose.waitUntil(DEBOUNCE_TIMEOUT_MILLIS) { reported == "1 + 1" }
  }

  /**
   * The regression this function's own doc comment explains: `CodeCellField`'s modifier reaches
   * the `BasicTextField` three layers down, not the node `RowScope.weight` reads parent data
   * from, so a weight passed straight through measured to the field's own near-zero intrinsic
   * width instead of filling the row - reachable but invisible.
   */
  @Test
  fun theInputFieldFillsMostOfTheRowRatherThanItsOwnIntrinsicWidth() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}) }
    }

    val field = compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).getUnclippedBoundsInRoot().width
    val row = compose.onRoot().getUnclippedBoundsInRoot().width
    assertTrue(
      "input field is only $field of a $row row - the row's weight is not reaching it",
      field >= row * MIN_FIELD_SHARE,
    )
  }

  @Test
  fun runIsDisabledWhileAnEntryIsRunning() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState(), running = true, onSubmit = {}) }
    }

    compose.onNodeWithContentDescription("Run").assertIsNotEnabled()
  }

  @Test
  fun runIsEnabledWhileNothingIsRunning() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}) }
    }

    compose.onNodeWithContentDescription("Run").assertIsEnabled()
  }

  /**
   * The prompt's symbol row, drawn inside the row here because nothing above it hosts one
   * (`WindowKeyRowTest.withoutAHostTheRowIsDrawnWhereItWasPublished`).
   */
  @Test
  fun theSymbolRowIsAbsentUntilThePromptHasTheCaret() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}) }
    }

    compose.onNodeWithTag(SYMBOL_ROW_TAG).assertDoesNotExist()
  }

  @Test
  fun aKeyFromTheSymbolRowTypesIntoThePrompt() {
    val state = TextFieldState()
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = state, running = false, onSubmit = {}) }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performClick()
    compose.onNodeWithText(":").performScrollTo().performClick()

    compose.runOnIdle { assertEquals(":", state.text.toString()) }
  }

  /** The prompt keeps its own history pair beside the field, so the row carries none. */
  @Test
  fun theSymbolRowCarriesNoSecondHistoryPair() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}) }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performClick()

    compose.onAllNodesWithContentDescription(RECALL_PREVIOUS_ENTRY).assertCountEquals(1)
  }

  @Test
  fun theSettingOffKeepsTheSymbolRowAway() {
    compose.setContent {
      JupydroidTheme {
        ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}, symbolRowShown = false)
      }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performClick()

    compose.onNodeWithTag(SYMBOL_ROW_TAG).assertDoesNotExist()
  }

  /** Real on-device evidence: a PNG of the row, pulled off the device - see `ConsoleTranscriptViewTest`. */
  @Test
  fun capturesAnInputRowScreenshotForEvidence() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState("1 + 1"), running = false, onSubmit = {}) }
    }
    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).assertIsDisplayed()

    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.getExternalFilesDir(null), EVIDENCE_FILE_NAME)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out) }
    assertEquals("expected a non-empty screenshot at ${file.absolutePath}", true, file.length() > 0)

    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.executeShellCommand("cp ${file.absolutePath} /sdcard/$EVIDENCE_FILE_NAME").use {
      it.fileDescriptor.let { fd -> java.io.FileInputStream(fd).use { stream -> stream.readBytes() } }
    }
  }

  /**
   * A second PNG rather than overwriting `EVIDENCE_FILE_NAME`: the row's shape changed with the
   * history pair, and the earlier screenshot documents what that row looked like then.
   */
  @Test
  fun capturesTheHistoryButtonsScreenshotForEvidence() {
    compose.setContent {
      JupydroidTheme { ConsoleInputRow(state = TextFieldState("1 + 1"), running = false, onSubmit = {}) }
    }
    compose.onNodeWithContentDescription(RECALL_PREVIOUS_ENTRY).assertIsDisplayed()

    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.getExternalFilesDir(null), HISTORY_EVIDENCE_FILE_NAME)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out) }
    assertEquals("expected a non-empty screenshot at ${file.absolutePath}", true, file.length() > 0)

    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.executeShellCommand("cp ${file.absolutePath} /sdcard/$HISTORY_EVIDENCE_FILE_NAME").use {
      it.fileDescriptor.let { fd -> java.io.FileInputStream(fd).use { stream -> stream.readBytes() } }
    }
  }

  private companion object {
    private const val PNG_QUALITY = 100
    private const val EVIDENCE_FILE_NAME = "console-input-row.png"
    private const val HISTORY_EVIDENCE_FILE_NAME = "console-input-row-history.png"
    private const val DEBOUNCE_TIMEOUT_MILLIS = 5_000L

    /**
     * Of the row, not of a width in dp: the three 48dp buttons take a fixed 144dp, so the field's share is
     * 0.5 of a 320dp row and 0.6 of a 360dp one, while an empty field measured at its own intrinsic width
     * is a few dp. 0.4 holds on every window the app runs in and fails when the weight is lost.
     */
    private const val MIN_FIELD_SHARE = 0.4f
  }
}
