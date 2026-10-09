package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
          inputRequest = null,
          onAnswerInput = {},
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
        CellOutputs(
          outputs = listOf(tallStream()),
          hidden = true,
          scrolled = true,
          search = emptyList(),
          onExpand = {},
          inputRequest = null,
          onAnswerInput = {},
        )
      }
    }

    compose.onNodeWithContentDescription(COLLAPSED_OUTPUTS).assertIsDisplayed()
  }

  /**
   * An output is code, so it is drawn at the one size code is drawn at.
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
            inputRequest = null,
            onAnswerInput = {},
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

  /**
   * A runaway print is one stream whose text keeps growing, so it is cut by lines: the head and
   * the tail are drawn, the middle is a row saying how much is hidden, and pressing it draws all.
   */
  @Test
  fun aLongOutputDrawsItsHeadAndTailUntilShowAllIsPressed() {
    showOutputs(listOf(linesOfOutput(HUNDRED)), scrolled = false)

    compose.onAllNodesWithText("line 50", substring = true).assertCountEquals(0)
    compose.onAllNodesWithText("line 99", substring = true).assertCountEquals(1)
    compose.onNodeWithContentDescription("70 lines hidden").assertIsDisplayed().performClick()
    compose.onAllNodesWithText("line 50", substring = true).assertCountEquals(1)
  }

  /**
   * Drawn whole, a long output is one text node per [OUTPUT_CHUNK_LINES] lines: each line a `Text`
   * draws hands its whole string to native code, so the chunk is what bounds that string.
   */
  @Test
  fun aLongOutputDrawnWholeHoldsAChunkOfLinesPerText() {
    showOutputs(listOf(linesOfOutput(CHUNKED)), scrolled = false)
    compose.onNodeWithContentDescription("${CHUNKED - OUTPUT_LINE_LIMIT} lines hidden").performClick()

    val texts = compose.onAllNodesWithText("line ", substring = true).fetchSemanticsNodes()
      .flatMap { it.config[SemanticsProperties.Text] }.map { it.text }
    assertTrue("${texts.map { it.lines().size }}", texts.all { it.trimEnd('\n').lines().size <= OUTPUT_CHUNK_LINES })
    // Lines, not characters: the final line feed is not in the node's text either way.
    assertEquals(linesOfOutput(CHUNKED).text.lines().dropLast(1), texts.joinToString("\n").trimEnd('\n').lines())
  }

  /** The seams between those text nodes add no height: drawn whole, the output is as tall as one `Text`. */
  @Test
  fun aLongOutputDrawnWholeIsAsTallAsOneText() {
    val stream = linesOfOutput(CHUNKED)
    compose.setContent {
      JupyterDroidTheme {
        Box {
          CellOutputs(
            outputs = listOf(stream),
            hidden = false,
            scrolled = false,
            search = emptyList(),
            onExpand = {},
            inputRequest = null,
            onAnswerInput = {},
          )
          Text(text = stream.text, style = codeTextStyle(), modifier = Modifier.testTag(CODE_LINE))
        }
      }
    }
    compose.onNodeWithContentDescription("${CHUNKED - OUTPUT_LINE_LIMIT} lines hidden").performClick()

    assertEquals(
      compose.onNodeWithTag(CODE_LINE).getUnclippedBoundsInRoot().height.value,
      compose.onNodeWithContentDescription("Cell output").getUnclippedBoundsInRoot().height.value,
      0.5f,
    )
  }

  /** A scrolled output already bounds its height, so it keeps every line until it has a cut of its own. */
  @Test
  fun aScrolledLongOutputIsNotCut() {
    showOutputs(listOf(linesOfOutput(HUNDRED)), scrolled = true)

    compose.onAllNodesWithText("line 50", substring = true).assertCountEquals(1)
  }

  /**
   * Many `display()` calls are many short outputs, which no line cut bounds, so they are cut by
   * count: the first [OUTPUT_COUNT_LIMIT] are drawn, then a row that draws the rest.
   */
  @Test
  fun manyOutputsDrawTheFirstFiftyUntilShowMoreIsPressed() {
    showOutputs(manyOutputs(), scrolled = false)

    compose.onAllNodesWithText("out ${OUTPUT_COUNT_LIMIT - 1}").assertCountEquals(1)
    compose.onAllNodesWithText("out $OUTPUT_COUNT_LIMIT").assertCountEquals(0)
    compose.onNodeWithContentDescription(SHOW_MORE_OUTPUTS).performScrollTo().performClick()
    compose.onAllNodesWithText("out ${RUNAWAY_OUTPUTS - 1}").assertCountEquals(1)
    compose.onAllNodesWithContentDescription(SHOW_MORE_OUTPUTS).assertCountEquals(0)
  }

  /** A scrolled box bounds the height, not the work of drawing every output inside it. */
  @Test
  fun aScrolledCellCapsItsOutputCountToo() {
    showOutputs(manyOutputs(), scrolled = true)

    compose.onAllNodesWithText("out $OUTPUT_COUNT_LIMIT").assertCountEquals(0)
  }

  /** Inside a scrolling column, as the editor's cell list is, so a row past the screen can be scrolled to. */
  private fun showOutputs(outputs: List<CellOutput>, scrolled: Boolean) {
    compose.setContent {
      JupyterDroidTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CellOutputs(
            outputs = outputs,
            hidden = false,
            scrolled = scrolled,
            search = emptyList(),
            onExpand = {},
            inputRequest = null,
            onAnswerInput = {},
          )
        }
      }
    }
  }

  private fun linesOfOutput(count: Int) = CellOutput.Stream(
    name = StreamName.STDOUT,
    text = (0 until count).joinToString("\n", postfix = "\n") { "line $it" },
  )

  /** One output per call, as `display(i)` gives - a list built directly, so no fold joins them. */
  private fun manyOutputs() = List(RUNAWAY_OUTPUTS) { CellOutput.Stream(name = StreamName.STDOUT, text = "out $it") }

  private fun oneLine() = CellOutput.Stream(name = StreamName.STDOUT, text = "one line")

  /** Fifty lines is comfortably past 24 - the reference's `max-height: 24em` - on any font size. */
  private fun tallStream() = CellOutput.Stream(
    name = StreamName.STDOUT,
    text = (1..FIFTY).joinToString("\n") { "line $it" },
  )

  private companion object {
    const val FIFTY = 50
    const val HUNDRED = 100
    const val RUNAWAY_OUTPUTS = 300

    /** Two whole chunks and part of a third, so both a seam and a short last chunk are drawn. */
    const val CHUNKED = 2 * OUTPUT_CHUNK_LINES + 500

    /** The reference line this test compares an output against - see the test's own docstring. */
    const val CODE_LINE = "one-line-of-code"
  }
}
