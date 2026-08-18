package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a collapsed heading shows in place of its section.
 *
 * Instrumented rather than JVM for [CellTouchTargetTest]'s reason: it is built from a bare
 * `clickable` rather than an `IconButton`, so nothing gives it Material's minimum for free and a
 * whole row of it could be 24dp tall with only this to notice.
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

  @Test
  fun theWholeRowIsBigEnoughToHit() {
    compose.setContent { HiddenCellsRow(count = 2, onExpand = {}) }

    compose.onNodeWithContentDescription("2 cells hidden")
      .assertIsAtLeastMinimumTouchTarget(compose.density)
  }
}
