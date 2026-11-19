package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.app.ui.PHONE_WIDTH
import com.jupydroid.app.ui.breaksInsideAWord
import com.jupydroid.app.ui.isCut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the notebook's last row has to be: something visible to press for each kind of cell, with no
 * menu opened first - the question no JVM test can answer, and the reason this is instrumented.
 */
@RunWith(AndroidJUnit4::class)
class AddCellFooterTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun showsBothKindsWithNoMenuOpen() {
    compose.setContent { AddCellFooter(onAdd = {}) }

    compose.onNodeWithText("Code").assertIsDisplayed()
    compose.onNodeWithText("Markdown").assertIsDisplayed()
  }

  @Test
  fun eachButtonReportsItsOwnKindOnce() {
    val reported = mutableListOf<CellKind>()
    compose.setContent { AddCellFooter(onAdd = { reported += it }) }

    compose.onNodeWithText("Code").performClick()
    compose.onNodeWithText("Markdown").performClick()

    assertEquals(listOf(CellKind.CODE, CellKind.MARKDOWN), reported)
  }

  @Test
  fun bothLabelsStayWholeAndApartOnTheNarrowestPhoneAtTwiceTheFontSize() {
    compose.setContent {
      Box(Modifier.requiredWidth(PHONE_WIDTH)) { AtFontScale(2f) { AddCellFooter(onAdd = {}) } }
    }

    listOf("Code", "Markdown").forEach { label ->
      assertFalse("$label is cut", label(label).isCut())
      assertFalse("$label breaks inside a word", label(label).breaksInsideAWord())
    }
    assertFalse("the two buttons overlap", boundsOf("Code").overlaps(boundsOf("Markdown")))
  }

  private fun label(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text), useUnmergedTree = true)

  private fun boundsOf(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
}
