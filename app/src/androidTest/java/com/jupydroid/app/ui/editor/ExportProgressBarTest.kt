package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An export runs nbconvert in the Space - on a tablet, behind a server that may have to be started
 * first - so seconds pass with nothing else on screen to say the row did anything.
 *
 * Instrumented because "is there something here to see" is not a question a JVM test can answer,
 * which is the reason `CellHeaderTest` exists; and because an indeterminate `LinearProgressIndicator`
 * carries no text of its own, so what is asserted is the name put on it.
 */
@RunWith(AndroidJUnit4::class)
class ExportProgressBarTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun namesTheFormatItIsConverting() {
    showProgress(ExportState(converting = "html"))

    compose.onNodeWithContentDescription(exportingLabel("html")).assertIsDisplayed()
  }

  /** Nothing is running, so nothing is drawn - not an empty bar taking a row of the app bar. */
  @Test
  fun drawsNothingWhileNothingIsConverting() {
    showProgress(ExportState())

    compose.onNodeWithContentDescription(exportingLabel("html")).assertDoesNotExist()
  }

  /** The picker asking the Space is not a conversion, and must not put a bar under the app bar. */
  @Test
  fun drawsNothingWhileTheFormatsAreStillBeingFetched() {
    showProgress(ExportState(loadingFormats = true))

    compose.onNodeWithContentDescription(exportingLabel("html")).assertDoesNotExist()
  }

  private fun showProgress(state: ExportState) {
    compose.setContent { JupydroidTheme { ExportProgressBar(state) } }
  }
}
