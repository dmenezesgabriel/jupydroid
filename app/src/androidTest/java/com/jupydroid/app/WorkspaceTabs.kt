package com.jupydroid.app

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.jupydroid.app.ui.RENAME_FIELD_TAG

/**
 * The explorer's row for the file named [title] - the node a test taps to open it, and whose
 * `selected` state says whether the pane beside it is that file.
 *
 * ```
 * compose.listingRow("a.ipynb").performClick()
 * ```
 */
fun ComposeTestRule.listingRow(title: String): SemanticsNodeInteraction =
  onNode(hasText(title) and hasContentDescription("File"))

/**
 * [listingRow] once it is drawn. The default waits past `EXPLORER_REFRESH_INTERVAL`, because a
 * row the disk gained appears only when the listing's poll re-reads the directory.
 *
 * ```
 * compose.waitForRow("b.ipynb").assertIsSelected()
 * ```
 */
fun ComposeTestRule.waitForRow(title: String, timeoutMillis: Long = ROW_TIMEOUT_MILLIS): SemanticsNodeInteraction {
  waitUntil(timeoutMillis) {
    onAllNodes(hasText(title) and hasContentDescription("File")).fetchSemanticsNodes().isNotEmpty()
  }
  return listingRow(title)
}

/**
 * Waits until the strip draws a tab titled [title].
 *
 * ```
 * compose.waitForTab("a.ipynb")
 * ```
 */
fun ComposeTestRule.waitForTab(title: String, timeoutMillis: Long = TAB_TIMEOUT_MILLIS) = waitUntil(timeoutMillis) {
  onAllNodesWithContentDescription("Tab: $title").fetchSemanticsNodes().isNotEmpty()
}

/**
 * Renames the document behind the tab titled [from] to [to], through the tab's long press - the
 * touch screen's right-click, whose menu's Rename opens `docmanager:rename`'s dialog.
 *
 * ```
 * compose.renameTab(from = "a.ipynb", to = "b.ipynb")
 * ```
 */
fun ComposeTestRule.renameTab(from: String, to: String) {
  onNodeWithContentDescription("Tab: $from").performTouchInput { longClick() }
  onNodeWithText("Rename…").performClick()
  onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
  onNodeWithTag(RENAME_FIELD_TAG).performTextInput(to)
  onNodeWithText("Rename").performClick()
}

private const val TAB_TIMEOUT_MILLIS = 5_000L
private const val ROW_TIMEOUT_MILLIS = 20_000L
