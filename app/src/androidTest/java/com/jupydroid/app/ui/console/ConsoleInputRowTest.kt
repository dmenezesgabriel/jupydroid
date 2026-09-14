package com.jupydroid.app.ui.console

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * The Console's input row - P6b-4b. `ConsoleViewModelTest` already covers what `submit()` does;
 * this is only whether a tap actually reaches it and whether Run is offered while running.
 */
@RunWith(AndroidJUnit4::class)
class ConsoleInputRowTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun typingAndTappingRunInvokesOnSubmit() {
    var submitted = false
    compose.setContent {
      JupyterDroidTheme {
        ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = { submitted = true })
      }
    }

    compose.onNodeWithTag(CONSOLE_INPUT_FIELD_TAG).performTextInput("1 + 1")
    compose.onNodeWithContentDescription("Run").performClick()

    assertTrue(submitted)
  }

  @Test
  fun runIsDisabledWhileAnEntryIsRunning() {
    compose.setContent {
      JupyterDroidTheme { ConsoleInputRow(state = TextFieldState(), running = true, onSubmit = {}) }
    }

    compose.onNodeWithContentDescription("Run").assertIsNotEnabled()
  }

  @Test
  fun runIsEnabledWhileNothingIsRunning() {
    compose.setContent {
      JupyterDroidTheme { ConsoleInputRow(state = TextFieldState(), running = false, onSubmit = {}) }
    }

    compose.onNodeWithContentDescription("Run").assertIsEnabled()
  }

  /** Real on-device evidence: a PNG of the row, pulled into `evidence/p6b-4/` - see `ConsoleTranscriptViewTest`. */
  @Test
  fun capturesAnInputRowScreenshotForEvidence() {
    compose.setContent {
      JupyterDroidTheme { ConsoleInputRow(state = TextFieldState("1 + 1"), running = false, onSubmit = {}) }
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

  private companion object {
    private const val PNG_QUALITY = 100
    private const val EVIDENCE_FILE_NAME = "p6b-4-console-input-row.png"
  }
}
