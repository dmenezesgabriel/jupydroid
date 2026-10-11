package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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

  /**
   * The box bounds a scrolled output's height, not its layout, so it has a cut of its own: the last
   * [SCROLLED_OUTPUT_LINE_LIMIT] lines, under a row that draws the rest.
   */
  @Test
  fun aScrolledLongOutputDrawsItsLastLinesUntilTheRowIsPressed() {
    showOutputs(listOf(linesOfOutput(SCROLLED_RUNAWAY)), scrolled = true)

    compose.onAllNodesWithText("line 0\n", substring = true).assertCountEquals(0)
    compose.onAllNodesWithText("line ${SCROLLED_RUNAWAY - 1}", substring = true).assertCountEquals(1)
    compose.onNodeWithContentDescription("$HUNDRED lines hidden").assertIsDisplayed().performClick()
    compose.onAllNodesWithText("line 0\n", substring = true).assertCountEquals(1)
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

  /**
   * Stepping onto a hit between head and tail draws the whole output, as landing on a match opens a
   * collapsed output area (`withMatchRevealed`), and it stays drawn once the arrows move on.
   */
  @Test
  fun steppingOntoAHitInTheHiddenLinesDrawsThemAll() {
    var search by mutableStateOf(listOf(hitOn("line 50", current = false)))
    showOutputs(listOf(linesOfOutput(HUNDRED)), scrolled = false) { search }
    compose.onNodeWithContentDescription("70 lines hidden").assertIsDisplayed()

    search = listOf(hitOn("line 50", current = true, stepped = false))
    compose.onNodeWithContentDescription("70 lines hidden").assertIsDisplayed()
    search = listOf(hitOn("line 50", current = true))
    compose.onAllNodesWithContentDescription("70 lines hidden").assertCountEquals(0)
    search = emptyList()
    compose.onAllNodesWithText("line 50", substring = true).assertCountEquals(1)
  }

  /** A hit already drawn in the head needs nothing drawn that was not. */
  @Test
  fun steppingOntoAHitInTheHeadKeepsTheCut() {
    showOutputs(listOf(linesOfOutput(HUNDRED)), scrolled = false) { listOf(hitOn("line 3\n", current = true)) }

    compose.onNodeWithContentDescription("70 lines hidden").assertIsDisplayed()
  }

  /** The count cut gives way the same way: stepping onto a hit in output 277 draws the rest. */
  @Test
  fun steppingOntoAHitPastTheFiftiethOutputDrawsTheRest() {
    var search by mutableStateOf(hitInOutput(RUNAWAY_HIT, current = false))
    showOutputs(manyOutputs(), scrolled = false) { search }
    compose.onNodeWithContentDescription(SHOW_MORE_OUTPUTS).assertExists()

    search = hitInOutput(RUNAWAY_HIT, current = true, stepped = false)
    compose.onNodeWithContentDescription(SHOW_MORE_OUTPUTS).assertExists()
    search = hitInOutput(RUNAWAY_HIT, current = true)
    compose.onAllNodesWithContentDescription(SHOW_MORE_OUTPUTS).assertCountEquals(0)
    compose.onAllNodesWithText("out $RUNAWAY_HIT").assertCountEquals(1)
  }

  /** [stepped] false is where a typed query lands: current, and not put in front of the reader. */
  private fun hitOn(needle: String, current: Boolean, stepped: Boolean = current): CellSearchHighlight {
    val at = linesOfOutput(HUNDRED).text.indexOf(needle)
    val range = at until at + needle.length
    return CellSearchHighlight(matches = listOf(range), current = range.takeIf { current }, currentStepped = stepped)
  }

  /** One entry per rendered output, as `outputSearchHighlightsOf` hands them, the hit on output [at]. */
  private fun hitInOutput(at: Int, current: Boolean, stepped: Boolean = current): List<CellSearchHighlight> {
    val hit = CellSearchHighlight(matches = listOf(0..2), current = (0..2).takeIf { current }, currentStepped = stepped)
    return List(RUNAWAY_OUTPUTS) { if (it == at) hit else CellSearchHighlight.NONE }
  }

  /**
   * Tapped on a device, so the pattern also runs on ART's ICU regex engine, which no JVM test reaches.
   * The output is the address alone, so the node's centre - where `performClick` lands - is on it.
   */
  @Test
  fun tappingAPrintedAddressFollowsIt() {
    val followed = RecordingUriHandler()
    showOutputs(listOf(CellOutput.Stream(StreamName.STDOUT, JUPYTER_URL)), scrolled = false, links = followed)

    compose.onNodeWithText(JUPYTER_URL).performClick()

    compose.waitUntil(LINK_TIMEOUT_MILLIS) { followed.opened == listOf(JUPYTER_URL) }
  }

  /** A traceback draws in its own tinted frame; the link inside it is tappable all the same. */
  @Test
  fun tappingAnAddressInATracebackFollowsIt() {
    val followed = RecordingUriHandler()
    val traceback = CellOutput.Error("ValueError", JUPYTER_URL, traceback = listOf(JUPYTER_URL))
    showOutputs(listOf(traceback), scrolled = false, links = followed)

    compose.onNodeWithText(JUPYTER_URL).performClick()

    compose.waitUntil(LINK_TIMEOUT_MILLIS) { followed.opened == listOf(JUPYTER_URL) }
  }

  /** The words around an address are text: a tap on them follows nothing. */
  @Test
  fun tappingBesideAnAddressFollowsNothing() {
    val followed = RecordingUriHandler()
    showOutputs(listOf(CellOutput.Stream(StreamName.STDOUT, "see $JUPYTER_URL")), scrolled = false, links = followed)

    compose.onNodeWithText("see $JUPYTER_URL").performTouchInput { click(Offset(1f, centerY)) }
    compose.waitForIdle()

    assertEquals(emptyList<String>(), followed.opened)
  }

  /** Inside a scrolling column, as the editor's cell list is, so a row past the screen can be scrolled to. */
  private fun showOutputs(
    outputs: List<CellOutput>,
    scrolled: Boolean,
    links: UriHandler = RecordingUriHandler(),
    search: () -> List<CellSearchHighlight> = { emptyList() },
  ) {
    compose.setContent {
      JupyterDroidTheme {
        CompositionLocalProvider(LocalUriHandler provides links) {
          Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            CellOutputs(
              outputs = outputs,
              hidden = false,
              scrolled = scrolled,
              search = search(),
              onExpand = {},
              inputRequest = null,
              onAnswerInput = {},
            )
          }
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

    /** An output well past the count cut, as `display(277)` is in the device flow. */
    const val RUNAWAY_HIT = 277

    /** Two whole chunks and part of a third, so both a seam and a short last chunk are drawn. */
    const val CHUNKED = 2 * OUTPUT_CHUNK_LINES + 500

    /** A hundred lines past what a scrolled box keeps, so its row reads `100 lines hidden`. */
    const val SCROLLED_RUNAWAY = SCROLLED_OUTPUT_LINE_LIMIT + HUNDRED

    /** The reference line this test compares an output against - see the test's own docstring. */
    const val CODE_LINE = "one-line-of-code"

    const val JUPYTER_URL = "https://jupyter.org"
    const val LINK_TIMEOUT_MILLIS = 5_000L
  }
}
