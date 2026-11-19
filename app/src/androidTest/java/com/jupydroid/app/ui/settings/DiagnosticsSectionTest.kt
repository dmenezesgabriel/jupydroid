package com.jupydroid.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeErrorLog
import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.app.ui.PHONE_WIDTH
import com.jupydroid.app.ui.isCut
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Diagnostics section owes a person the log's size before it deletes anything, and a way back out. */
@RunWith(AndroidJUnit4::class)
class DiagnosticsSectionTest {
  @get:Rule val compose = createComposeRule()

  private val log = FakeErrorLog()

  private fun logHoldingTwoEntries() = runBlocking {
    log.append(ErrorLogEntry(timestampMillis = 1, kind = ErrorLogKind.CRASH, message = "boom"))
    log.append(ErrorLogEntry(timestampMillis = 2, kind = ErrorLogKind.EXIT, message = "gone"))
  }

  private fun show(fontScale: Float = 1f) {
    val viewModel = DiagnosticsViewModel(log)
    compose.setContent {
      JupyterDroidTheme {
        Box(Modifier.requiredWidth(PHONE_WIDTH)) { AtFontScale(fontScale) { DiagnosticsSection(viewModel) } }
      }
    }
  }

  private fun sizeText() = byteSizeText(runBlocking { log.summary().sizeBytes })

  private fun summaryText() = "2 entries · ${sizeText()}"

  @Test
  fun anEmptyLogReadsZeroAndOffersNothingToClear() {
    show()

    compose.onNodeWithText("0 entries · 0 B").assertIsDisplayed()
    compose.onNodeWithText(CLEAR_LOGS).assertIsNotEnabled()
  }

  @Test
  fun theSectionStatesHowManyEntriesAndHowMuchStorage() {
    logHoldingTwoEntries()

    show()

    compose.onNodeWithText(summaryText()).assertIsDisplayed()
  }

  @Test
  fun clearLogsAsksFirstAndStatesTheSizeItFrees() {
    logHoldingTwoEntries()
    show()

    compose.onNodeWithText(CLEAR_LOGS).performClick()

    compose.onNodeWithText("Clear 2 entries (${sizeText()})? This cannot be undone.").assertIsDisplayed()
    assertEquals(2, log.entries.size)
  }

  @Test
  fun cancellingKeepsTheEntries() {
    logHoldingTwoEntries()
    show()
    compose.onNodeWithText(CLEAR_LOGS).performClick()

    compose.onNodeWithText("Cancel").performClick()

    compose.onNodeWithText(summaryText()).assertIsDisplayed()
    assertEquals(2, log.entries.size)
  }

  @Test
  fun confirmingLeavesAnEmptyLog() {
    logHoldingTwoEntries()
    show()
    compose.onNodeWithText(CLEAR_LOGS).performClick()

    compose.onNodeWithContentDescription("Confirm $CLEAR_LOGS").performClick()

    compose.onNodeWithText("0 entries · 0 B").assertIsDisplayed()
    assertEquals(0, log.entries.size)
  }

  @Test
  fun theSummaryAndTheRowAreWholeAtTwiceTheFontScale() {
    logHoldingTwoEntries()
    show(fontScale = 2f)

    assertFalse("the summary is cut", compose.onNodeWithText(summaryText()).isCut())
    assertFalse("the row is cut", compose.onNodeWithText(CLEAR_LOGS).isCut())
  }
}
