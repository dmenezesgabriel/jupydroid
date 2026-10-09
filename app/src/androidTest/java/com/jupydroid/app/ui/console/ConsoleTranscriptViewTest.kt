package com.jupydroid.app.ui.console

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.ExecutionStatus
import com.jupydroid.app.ui.editor.INPUT_PROMPT_DESCRIPTION
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Read-only transcript rendering, against a fake/static list - `ConsoleTranscript` reuses
 * `CellOutputs`/`executionLabel` exactly as the notebook editor's own cells do (see the entries'
 * own doc comment on `ConsoleEntry`). No kernel, no input row, no navigation entry - those have
 * tests of their own.
 */
@RunWith(AndroidJUnit4::class)
class ConsoleTranscriptViewTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun eachEntrysInputIsDisplayed() {
    compose.setContent { JupyterDroidTheme { ConsoleTranscript(ENTRIES, onAnswerInput = { _, _ -> }) } }

    compose.onNodeWithText("1 + 1").assertIsDisplayed()
    compose.onNodeWithText("raise ValueError('boom')").assertIsDisplayed()
    compose.onNodeWithText("import time; time.sleep(9)").assertIsDisplayed()
  }

  /** Bracket notation, not the `>>>` REPL style - see the plan's own design decision. */
  @Test
  fun eachEntrysPromptShowsItsPositionInTheTranscript() {
    compose.setContent { JupyterDroidTheme { ConsoleTranscript(ENTRIES, onAnswerInput = { _, _ -> }) } }

    compose.onNodeWithText("[1]:").assertIsDisplayed()
    compose.onNodeWithText("[2]:").assertIsDisplayed()
    // Running has no count yet - the reference's own "still going" notation.
    compose.onNodeWithText("[*]:").assertIsDisplayed()
  }

  /**
   * `CellOutputs` draws nothing at all for an entry with no outputs (`CellOutputsView.kt`'s own
   * early return), so the running entry - still executing, nothing back yet - contributes no
   * "Cell output" node. Two of the three entries do.
   */
  @Test
  fun outputsRenderThroughTheReusedCellOutputsComposable() {
    compose.setContent { JupyterDroidTheme { ConsoleTranscript(ENTRIES, onAnswerInput = { _, _ -> }) } }

    compose.onAllNodesWithContentDescription("Cell output").assertCountEquals(2)
  }

  /**
   * Real on-device evidence: a PNG of the fixture transcript.
   *
   * Written first to the app's own external-files dir (writable with no permission grant), then
   * `cp`'d to `/sdcard` **as the shell user**, via `UiAutomation.executeShellCommand` -
   * `connectedDebugAndroidTest` uninstalls both the app and the test APK the moment the task
   * finishes, which deletes the app-private copy along with it; `/sdcard` is not app-owned
   * storage, so a copy placed there by shell survives long enough for `adb pull` afterward.
   */
  @Test
  fun capturesATranscriptScreenshotForEvidence() {
    compose.setContent { JupyterDroidTheme { ConsoleTranscript(ENTRIES, onAnswerInput = { _, _ -> }) } }

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

  /** An entry blocked in `input()` is answered from the transcript, as a notebook cell is. */
  @Test
  fun anEntryWaitingOnInputDrawsAFieldThatAnswersIt() {
    val answered = mutableListOf<Pair<String, String>>()
    val asking = ConsoleEntry(
      id = "e9",
      input = "input('Again?')",
      outputs = emptyList(),
      status = ExecutionStatus.RUNNING,
      inputRequest = KernelInputRequest(prompt = "Again?", password = false, requestHeader = "{}"),
    )
    compose.setContent {
      JupyterDroidTheme {
        ConsoleTranscript(listOf(asking), onAnswerInput = { id, answer -> answered += id to answer })
      }
    }

    compose.onNodeWithText("Again?").assertIsDisplayed()
    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).performTextInput("yes")
    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).performImeAction()

    assertEquals(listOf("e9" to "yes"), answered)
  }

  private companion object {
    private const val PNG_QUALITY = 100
    private const val EVIDENCE_FILE_NAME = "p6b-3-console-transcript.png"

    val ENTRIES = listOf(
      ConsoleEntry(
        id = "e1",
        input = "1 + 1",
        outputs = listOf(CellOutput.Stream(StreamName.STDOUT, "2\n")),
        status = ExecutionStatus.STOPPED_OK,
        executionCount = 1,
      ),
      ConsoleEntry(
        id = "e2",
        input = "raise ValueError('boom')",
        outputs = listOf(
          CellOutput.Error(
            name = "ValueError",
            value = "boom",
            traceback = listOf("Traceback (most recent call last):", "ValueError: boom"),
          ),
        ),
        status = ExecutionStatus.STOPPED_ERROR,
        executionCount = 2,
      ),
      ConsoleEntry(
        id = "e3",
        input = "import time; time.sleep(9)",
        outputs = emptyList(),
        status = ExecutionStatus.RUNNING,
      ),
    )
  }
}
