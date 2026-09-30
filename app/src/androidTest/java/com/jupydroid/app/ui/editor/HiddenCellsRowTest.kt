package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.assertReservesMinimumTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a collapsed heading shows in place of its section.
 *
 * Instrumented rather than JVM because the question is a height: it is built from a bare
 * `clickable` rather than an `IconButton`, so nothing reserves Material's minimum for it and the
 * icon inside decides the row - 8 + 24 + 8 = 40dp, which is what this shipped as.
 */
@RunWith(AndroidJUnit4::class)
class HiddenCellsRowTest {
  @get:Rule val compose = createComposeRule()

  /** `trans._n('%1 cell hidden', '%1 cells hidden', …)` - the reference pluralises, so this does. */
  @Test
  fun namesHowManyCellsWentAndPluralisesLikeTheReference() {
    compose.setContent { HiddenCellsRow(count = 1, onExpand = {}) }

    compose.onNodeWithContentDescription("1 cell hidden").assertIsDisplayed()
  }

  @Test
  fun aSectionOfSeveralReadsAsSeveral() {
    compose.setContent { HiddenCellsRow(count = 3, onExpand = {}) }

    compose.onNodeWithContentDescription("3 cells hidden").assertIsDisplayed()
  }

  /** `onclick: this.headingCollapsed = false` (`packages/cells/src/widget.ts:2438`). */
  @Test
  fun tappingItAsksToBringThemBack() {
    var expanded = 0
    compose.setContent { HiddenCellsRow(count = 2, onExpand = { expanded++ }) }

    compose.onNodeWithContentDescription("2 cells hidden").performClick()

    assertEquals(1, expanded)
  }

  /**
   * This test existed and passed while the row was 40dp tall, because it asked with
   * `touchBoundsInRoot` - which pads any clickable out to 48dp before answering. The reservation
   * is what a neighbour can take away; see `assertReservesMinimumTouchTarget`.
   */
  @Test
  fun theWholeRowReservesEnoughSpaceToHit() {
    compose.setContent { HiddenCellsRow(count = 2, onExpand = {}) }

    compose.onNodeWithContentDescription("2 cells hidden").assertReservesMinimumTouchTarget()
  }
}
