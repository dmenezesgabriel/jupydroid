package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
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
   * **Measured against a line of code, not against `CodeText.lineHeight.toDp()`**, and the
   * difference is a device rather than a preference. This asserted the conversion until the tablet
   * ran it: at `fontScale = 1.1` Android 14's *non-linear* font scaling makes `18.sp.toDp()` come
   * out at **19.2dp**, while a line of that very style lays out to **20.67dp** - so the assertion
   * failed on a build whose output and source were pixel-identical at 20.67dp each. It had been
   * green only because `dev36` sits at `fontScale = 1.0`, where a conversion and a layout happen
   * to agree.
   *
   * A conversion is not the claim. The claim is that an output line and a source line are the same
   * height, and comparing the two is both what it says and the only form of it that survives the
   * reader's font setting - which is the whole reason [CodeText] is in `sp`.
   */
  @Test
  fun oneOutputLineIsAsTallAsALineOfCode() {
    compose.setContent {
      JupyterDroidTheme {
        Box {
          CellOutputs(
            outputs = listOf(oneLine()),
            hidden = false,
            scrolled = false,
            search = emptyList(),
            onExpand = {},
          )
          Text(text = "one line", style = codeTextStyle(), modifier = Modifier.testTag(CODE_LINE))
        }
      }
    }

    assertEquals(
      compose.onNodeWithTag(CODE_LINE).getUnclippedBoundsInRoot().height.value,
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

    /** The reference line this test compares an output against - see the test's own docstring. */
    const val CODE_LINE = "one-line-of-code"
  }
}
