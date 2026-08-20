package com.jupydroid.app.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.theme.CodeText
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `notebook:enable-output-scrolling`'s draw, split out of `EditorScreen` alongside `CellOutputs`
 * itself. What is asserted is the **bound**, not the pixels of the reference's `24em` - a test
 * naming a `dp` value would be asserting the font metrics of whichever device ran it, which is
 * the same call `LineNumberGutterTest` makes for the gutter.
 */
@RunWith(AndroidJUnit4::class)
class CellOutputsViewTest {
  @get:Rule val compose = createComposeRule()

  /** One composition, toggled - two `setContent` calls on one activity is a framework error. */
  @Test
  fun aScrolledCellIsShorterThanTheSameOutputUnscrolled() {
    var scrolled by mutableStateOf(false)
    compose.setContent {
      JupyterDroidTheme {
        CellOutputs(
          outputs = listOf(
            tallStream(),
          ),
          hidden = false,
          scrolled = scrolled,
          search = emptyList(),
          onExpand = {
          },
        )
      }
    }

    val unscrolledHeight = compose.onNodeWithContentDescription("Cell output").getUnclippedBoundsInRoot().height.value
    scrolled = true
    compose.waitForIdle()
    val scrolledHeight = compose.onNodeWithContentDescription("Cell output").getUnclippedBoundsInRoot().height.value

    assertTrue(
      "expected the scrolled output ($scrolledHeight) to be bounded below the unscrolled one " +
        "($unscrolledHeight)",
      scrolledHeight < unscrolledHeight,
    )
  }

  /** Collapsed beats scrolled - there is no height to bound when nothing is drawn at all. */
  @Test
  fun aHiddenCellStaysCollapsedEvenWhenScrolled() {
    compose.setContent {
      JupyterDroidTheme {
        CellOutputs(outputs = listOf(tallStream()), hidden = true, scrolled = true, search = emptyList(), onExpand = {})
      }
    }

    compose.onNodeWithContentDescription(COLLAPSED_OUTPUTS).assertIsDisplayed()
  }

  /**
   * P2l-3a: an output is code, so it is drawn at the one size code is drawn at.
   *
   * **Derived from `CodeText`, not a `dp` this file spells out** - the rule this suite already
   * states for the scrolled bound. What makes it exact rather than font metrics is that the line
   * height is *stated*: Material's `DefaultLineHeightStyle` is `trim = None`, so a single line
   * lays out to exactly `lineHeight`. It measured `bodySmall`'s 16sp before this step, against a
   * cell source resolving to 14sp with the font's own line height.
   */
  @Test
  fun oneOutputLineIsAsTallAsTheCodeLineHeight() {
    compose.setContent {
      JupyterDroidTheme {
        CellOutputs(outputs = listOf(oneLine()), hidden = false, scrolled = false, search = emptyList(), onExpand = {})
      }
    }

    val expected = with(compose.density) { CodeText.lineHeight.toDp() }
    assertEquals(
      expected.value,
      compose.onNodeWithContentDescription("Cell output").getUnclippedBoundsInRoot().height.value,
      0.5f,
    )
  }

  private fun oneLine() = CellOutput.Stream(name = StreamName.STDOUT, text = "one line")

  /** Fifty lines is comfortably past 24 - the reference's `max-height: 24em` - on any font size. */
  private fun tallStream() = CellOutput.Stream(
    name = StreamName.STDOUT,
    text = (1..FIFTY).joinToString("\n") { "line $it" },
  )

  private companion object {
    const val FIFTY = 50
  }
}
