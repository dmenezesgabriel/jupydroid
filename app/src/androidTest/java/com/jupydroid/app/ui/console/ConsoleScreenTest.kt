package com.jupydroid.app.ui.console

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.NotebookKernelSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * P6b-9 sub-step 1: the Console's own screen - [ConsoleTranscript] and [ConsoleInputRow] under a
 * bar, wired to a real [ConsoleViewModel]. No navigation yet: this only proves the screen composes
 * and the wiring reaches the pieces P6b-2 through P6b-7 already built and tested on their own.
 */
@RunWith(AndroidJUnit4::class)
class ConsoleScreenTest {
  @get:Rule val compose = createComposeRule()

  /**
   * The Console's bar carries its own actions and nothing else - leaving is the tab strip's act
   * (the ✕, or the destination-named arrow below two panes), which is `MainAreaTabStripTest`'s
   * surface now. This asserts only that no dismiss control leaked back onto the bar.
   */
  @Test
  fun theBarCarriesNoDismissControl() {
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          viewModel = consoleViewModel(),
          editorSettingsRepository = FakeEditorSettingsRepository(),
        )
      }
    }

    compose.onAllNodesWithContentDescription("Back", substring = true).assertCountEquals(0)
  }

  @Test
  fun submittingInputRendersTheEntryInTheTranscript() {
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          viewModel = consoleViewModel(),
          editorSettingsRepository = FakeEditorSettingsRepository(),
        )
      }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()

    // Two, not one: FakeKernelGateway echoes the code back as its own output, so the entry's own
    // input line and its rendered output both read "1 + 1" once the run resolves.
    compose.onAllNodesWithText("1 + 1").assertCountEquals(2)
  }

  @Test
  fun tappingInterruptKernelCancelsTheRunningEntry() {
    val gateway = InterruptibleKernelGateway()
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          viewModel = consoleViewModel(gateway),
          editorSettingsRepository = FakeEditorSettingsRepository(),
        )
      }
    }
    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()

    compose.onNodeWithContentDescription("Console actions").performClick()
    compose.onNodeWithText(INTERRUPT_KERNEL).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.interruptCount == 1 }
  }

  @Test
  fun interruptKernelIsDisabledWhileNothingIsRunning() {
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          viewModel = consoleViewModel(),
          editorSettingsRepository = FakeEditorSettingsRepository(),
        )
      }
    }

    compose.onNodeWithContentDescription("Console actions").performClick()

    compose.onNodeWithText(INTERRUPT_KERNEL).assertIsNotEnabled()
  }

  /**
   * `fileeditor:toggle-autoclosing-brackets-universal` names the Console too
   * (`commands.ts:552-575`), but this field ran `CodeCellField`'s default rules - a `(` with the
   * setting off closed itself here while the notebook's stayed open. The same hole the symbol
   * row had, fixed by the same plumbing.
   */
  @Test
  fun aBracketStaysOpenWhenAutoClosingIsOff() {
    val viewModel = consoleViewModel()
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          viewModel = viewModel,
          editorSettingsRepository = FakeEditorSettingsRepository(
            EditorSettings(autoClosingBrackets = false),
          ),
        )
      }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("print(")

    assertEquals("print(", viewModel.input.text.toString())
  }

  @Test
  fun tappingClearRemovesTheTranscript() {
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          viewModel = consoleViewModel(),
          editorSettingsRepository = FakeEditorSettingsRepository(),
        )
      }
    }
    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()
    compose.onAllNodesWithText("1 + 1").assertCountEquals(2)

    compose.onNodeWithContentDescription("Console actions").performClick()
    compose.onNodeWithText(CLEAR_CONSOLE_CELLS).performClick()

    compose.onAllNodesWithText("1 + 1").assertCountEquals(0)
  }

  /** Real on-device evidence: a PNG of the whole screen. */
  @Test
  fun capturesTheScreenScreenshotForEvidence() {
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          viewModel = consoleViewModel(),
          editorSettingsRepository = FakeEditorSettingsRepository(),
        )
      }
    }
    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()
    compose.onAllNodesWithText("1 + 1").assertCountEquals(2)

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

  private fun consoleViewModel(gateway: KernelGateway = FakeKernelGateway()): ConsoleViewModel {
    val session = NotebookKernelSession(gateway, SPACE, attachedTo = ATTACHED_SESSION) { "Analysis.ipynb" }
    return ConsoleViewModel(gateway, session)
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
    val ATTACHED_SESSION = KernelSession(
      id = "s1",
      spaceId = "base",
      kernelId = "k1",
      kernelName = "python3",
      serverSessionId = "ss1",
    )
    const val PNG_QUALITY = 100
    const val EVIDENCE_FILE_NAME = "p6b-9-console-screen.png"
    const val TIMEOUT_MILLIS = 5_000L
  }
}

/**
 * Hangs `execute()` until [interrupt] is called, the androidTest source set's own copy of the JVM
 * suite's `RecordingKernelGateway(runsUntilInterrupted = true)` - `StallingKernelGateway`'s own
 * precedent for why a source-set-local double exists rather than an import: `src/androidTest`
 * cannot see `src/test`.
 */
private class InterruptibleKernelGateway(private val fake: KernelGateway = FakeKernelGateway()) :
  KernelGateway by fake {
  private val interrupted = CompletableDeferred<Unit>()
  var interruptCount = 0
    private set

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
    interrupted.await()
    emit(KernelEvent.Output(cellId, CellOutput.Stream(StreamName.STDOUT, code)))
    emit(KernelEvent.Idle)
  }

  override suspend fun interrupt(session: KernelSession) {
    interruptCount++
    interrupted.complete(Unit)
  }
}
