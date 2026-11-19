package com.jupydroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeErrorLog
import com.jupydroid.app.data.fake.FakeErrorLogSharer
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
  private val sharer = FakeErrorLogSharer()

  private fun logHoldingTwoEntries() = runBlocking {
    log.append(ErrorLogEntry(timestampMillis = 1, kind = ErrorLogKind.CRASH, message = "boom"))
    log.append(ErrorLogEntry(timestampMillis = 2, kind = ErrorLogKind.EXIT, message = "gone"))
  }

  private fun show(fontScale: Float = 1f) {
    val viewModel = DiagnosticsViewModel(log, sharer)
    compose.setContent {
      JupyterDroidTheme {
        // A `Column`, as Settings lays the section out: in a `Box` the rows stack and the last one takes every click.
        Column(Modifier.requiredWidth(PHONE_WIDTH)) { AtFontScale(fontScale) { DiagnosticsSection(viewModel) } }
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
  fun anEmptyLogOffersNothingToShare() {
    show()

    compose.onNodeWithText(SHARE_LOG).assertIsNotEnabled()
  }

  @Test
  fun shareLogOffersTheLogOnce() {
    logHoldingTwoEntries()
    show()
    // The view model loads off the test thread, and a row clicked while it still reads is disabled and drops
    // the click: wait for the summary rather than racing it.
    compose.waitUntil { compose.onAllNodesWithText(summaryText()).fetchSemanticsNodes().isNotEmpty() }

    compose.onNodeWithText(SHARE_LOG).assertIsEnabled()
    compose.onNodeWithText(SHARE_LOG).performClick()

    compose.waitForIdle()
    assertEquals(1, sharer.shareCount)
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
    assertFalse("the share row is cut", compose.onNodeWithText(SHARE_LOG).isCut())
  }
}
