package com.jupydroid.app

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.jupydroid.app.domain.model.BASE_SPACE

/** The accessible name of base's Open on the Spaces home - what a test presses to enter it. */
val OPEN_BASE_SPACE = "Open ${BASE_SPACE.name} (${BASE_SPACE.id})"

/**
 * Walks from the Spaces home, where [MainNavigation] starts, into base's workspace - the step
 * every test about the workspace takes before its own.
 *
 * ```
 * compose.setContent { MainNavigation(…) }
 * compose.enterBaseSpace()
 * ```
 */
fun ComposeContentTestRule.enterBaseSpace() {
  waitUntil(ENTER_TIMEOUT_MILLIS) {
    onAllNodesWithContentDescription(OPEN_BASE_SPACE).fetchSemanticsNodes().isNotEmpty()
  }
  onNodeWithContentDescription(OPEN_BASE_SPACE).performClick()
  // Until the home has left, not just until the tap landed: a test that freezes the clock next
  // would otherwise freeze it mid-transition, with the workspace not yet drawn.
  waitUntil(ENTER_TIMEOUT_MILLIS) {
    onAllNodesWithContentDescription(OPEN_BASE_SPACE).fetchSemanticsNodes().isEmpty()
  }
}

private const val ENTER_TIMEOUT_MILLIS = 5_000L
