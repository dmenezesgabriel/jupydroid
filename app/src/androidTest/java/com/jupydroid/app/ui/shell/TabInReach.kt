package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo

/**
 * The tab named [title] after the strip has been scrolled to it, as a user on a narrow window
 * scrolls to reach it: the strip shows the active tab whole, so a press aimed at the middle of one
 * beyond the edge lands on whatever is drawn there (`MainAreaTabStripRevealTest`).
 */
internal fun ComposeTestRule.tabInReach(title: String): SemanticsNodeInteraction =
  onNodeWithContentDescription("Tab: $title").performScrollTo()
