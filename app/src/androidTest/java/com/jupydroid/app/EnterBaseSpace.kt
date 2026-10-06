package com.jupydroid.app

import androidx.compose.ui.semantics.SemanticsProperties
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
fun ComposeContentTestRule.enterBaseSpace() = enterSpace(BASE_SPACE.name, BASE_SPACE.id)

/**
 * Walks from the Spaces home into the workspace of the Space named [name] with id [spaceId]. It is
 * run first when it reads Stopped, since the home enables Open only on a running Space.
 *
 * ```
 * compose.enterSpace("Thesis", "space-1")
 * ```
 */
fun ComposeContentTestRule.enterSpace(name: String, spaceId: String) {
  val open = "Open $name ($spaceId)"
  val run = "Run $name ($spaceId)"
  waitUntil(ENTER_TIMEOUT_MILLIS) {
    onAllNodesWithContentDescription(open).fetchSemanticsNodes().isNotEmpty()
  }
  if (onAllNodesWithContentDescription(run).fetchSemanticsNodes().isNotEmpty()) {
    onNodeWithContentDescription(run).performClick()
  }
  waitUntil(ENTER_TIMEOUT_MILLIS) {
    onAllNodesWithContentDescription(open).fetchSemanticsNodes().any {
      SemanticsProperties.Disabled !in it.config
    }
  }
  onNodeWithContentDescription(open).performClick()
  // Until the home has left, not just until the tap landed: a test that freezes the clock next
  // would otherwise freeze it mid-transition, with the workspace not yet drawn.
  waitUntil(ENTER_TIMEOUT_MILLIS) {
    onAllNodesWithContentDescription(open).fetchSemanticsNodes().isEmpty()
  }
}

private const val ENTER_TIMEOUT_MILLIS = 5_000L
