package com.jupydroid.app.ui.console

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.NotebookKernelSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  @Test
  fun tappingBackInvokesOnBack() {
    var backTapped = false
    compose.setContent {
      JupyterDroidTheme {
        ConsoleScreen(
          title = "Analysis.ipynb",
          viewModel = consoleViewModel(),
          onBack = { backTapped = true },
        )
      }
    }

    compose.onNodeWithContentDescription("Back to notebook").performClick()

    assertTrue(backTapped)
  }

  @Test
  fun submittingInputRendersTheEntryInTheTranscript() {
    compose.setContent {
      JupyterDroidTheme { ConsoleScreen(title = "Analysis.ipynb", viewModel = consoleViewModel(), onBack = {}) }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()

    // Two, not one: FakeKernelGateway echoes the code back as its own output, so the entry's own
    // input line and its rendered output both read "1 + 1" once the run resolves.
    compose.onAllNodesWithText("1 + 1").assertCountEquals(2)
  }

  @Test
  fun tappingClearRemovesTheTranscript() {
    compose.setContent {
      JupyterDroidTheme { ConsoleScreen(title = "Analysis.ipynb", viewModel = consoleViewModel(), onBack = {}) }
    }
    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()
    compose.onAllNodesWithText("1 + 1").assertCountEquals(2)

    compose.onNodeWithContentDescription("Console actions").performClick()
    compose.onNodeWithText(CLEAR_CONSOLE_CELLS).performClick()

    compose.onAllNodesWithText("1 + 1").assertCountEquals(0)
  }

  /** Real on-device evidence: a PNG of the whole screen, pulled into `evidence/p6b-9/`. */
  @Test
  fun capturesTheScreenScreenshotForEvidence() {
    compose.setContent {
      JupyterDroidTheme { ConsoleScreen(title = "Analysis.ipynb", viewModel = consoleViewModel(), onBack = {}) }
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
    val ATTACHED_SESSION = KernelSession(id = "s1", spaceId = "base", kernelId = "k1", serverSessionId = "ss1")
    const val PNG_QUALITY = 100
    const val EVIDENCE_FILE_NAME = "p6b-9-console-screen.png"
  }
}
