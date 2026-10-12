package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * **An image opened in the file editor: the reason is on screen, and there is nothing to type into.**
 *
 * Over a real directory, so the refusal is the disk read's own. Whether a field exists is the half
 * no JVM test can answer: a field drawn over an unloaded document would take a keystroke, and a
 * keystroke is what gets saved over the image.
 */
@RunWith(AndroidJUnit4::class)
class FileEditorNotTextScreenTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Test
  fun anImageShowsWhyItIsNotOpenedAndOffersNoField() {
    val image = File(temp.root, "plot.png").apply { writeBytes(PNG_HEADER) }

    compose.setContent {
      JupyterDroidTheme {
        FileEditorScreen(
          path = "plot.png",
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          applicationScope = scope,
          editorSettingsRepository = FakeEditorSettingsRepository(),
          onBack = {},
          openDocuments = OpenDocuments(),
        )
      }
    }

    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText(REASON, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText(REASON, substring = true).assertIsDisplayed()
    compose.onNode(hasSetTextAction()).assertDoesNotExist()
    assertArrayEquals(PNG_HEADER, image.readBytes())
  }
}

private const val REASON = "'plot.png' is not UTF-8 text"

private val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)

/** The read has to fail before there is anything to look at. */
private const val TIMEOUT_MILLIS = 5_000L
