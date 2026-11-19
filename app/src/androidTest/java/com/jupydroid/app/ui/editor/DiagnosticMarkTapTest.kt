package com.jupydroid.app.ui.editor

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A finding is read from its **squiggled range** - VS Code answers it on the marker's hover
 * (`markerHoverParticipant.ts`), a hover a phone performs as a tap - and the gutter beside it is
 * the breakpoint's alone: the same line can hold both and each tap does one thing.
 *
 * The squiggle is canvas, not a semantics node (`BreakpointGutterTapTest` records the same
 * drawBehind limitation), so what is asserted is the tap's *effect*: the popup's text, the line
 * the breakpoint callback heard, and where the caret went.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticMarkTapTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun tappingASquiggledRangeShowsItsFindingsAndPlacesTheCaret() {
    val state = setContent()
    focusField()

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideRangePx(), rowMiddlePx(line = 2))) }

    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsDisplayed()
    compose.onNodeWithText("error: undefined name 'never_defined'", substring = true).assertIsDisplayed()
    compose.onNodeWithText("pyflakes", substring = true).assertIsDisplayed()
    compose.onNodeWithText("E501", substring = true).assertIsDisplayed()
    compose.waitUntil(CARET_TIMEOUT_MS) { state.selection.start in RANGE_START..RANGE_END }
  }

  @Test
  fun tappingWhereTheOldMarkWasTogglesABreakpointAndShowsNoCard() {
    var tapped: Int? = null
    setContent(onGutterTap = { tapped = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(oldMarkPx(), rowMiddlePx(line = 2))) }

    assertEquals(2, tapped)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  @Test
  fun tappingTheNumbersOfADiagnosedLineStillTogglesABreakpoint() {
    var tapped: Int? = null
    setContent(onGutterTap = { tapped = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideNumbersPx(), rowMiddlePx(line = 2))) }

    assertEquals(2, tapped)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  @Test
  fun tappingUndiagnosedCodeShowsNoCard() {
    setContent()

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideRangePx(), rowMiddlePx(line = 1))) }

    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  /** A hint's span is two characters, 16.5dp: a finger beside it, not on it, still reads the finding. */
  @Test
  fun tappingBesideAHintsTwoCharactersShowsItsFinding() {
    setContent(diagnostics = listOf(HINT))

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      click(Offset(gutterWidthPx() + HINT_BESIDE.toPx(), rowMiddlePx(line = 1)))
    }

    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun aLineHoldingBothKeepsOneMeaningPerTargetAt320dp() = bothTargetsAt(320.dp)

  @Test
  fun aLineHoldingBothKeepsOneMeaningPerTargetAt360dp() = bothTargetsAt(360.dp)

  @Test
  fun aLineHoldingBothKeepsOneMeaningPerTargetAt412dp() = bothTargetsAt(412.dp)

  /** The condition editor stays the gutter's: armed and held opens it, and no card with it. */
  @Test
  fun longPressingAnArmedDiagnosedLinesGutterStillOpensTheConditionEditor() {
    var longPressed: Int? = null
    setContent(breakpoints = listOf(Breakpoint(line = 2)), onGutterLongPress = { longPressed = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { longClick(Offset(oldMarkPx(), rowMiddlePx(line = 2))) }

    assertEquals(2, longPressed)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  /**
   * The card anchors on the whole of the diagnosed line, so its top edge belongs at or below the
   * line's bottom and its left edge at the content's left. Read in one space - the screen,
   * through [boundsOnScreen] on the popup's node, on the text's node (the inner region, per
   * [gutterWidthPx]) and on the wrapper (the padded box the `Popup` anchors on).
   */
  @Test
  fun theDetailCardsTopClearsTheBottomOfTheLineItDescribes() {
    setContent(boxed = true)
    focusField()

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideRangePx(), rowMiddlePx(line = 2))) }
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsDisplayed()
    compose.waitForIdle()

    val popup = compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).fetchSemanticsNode().boundsOnScreen()
    val inner = compose.onNodeWithText(FIRST_LINE, substring = true).fetchSemanticsNode().boundsOnScreen()
    val box = compose.onNodeWithTag(FIELD_TAG).fetchSemanticsNode().boundsOnScreen()
    assertPopupAlignedToAnchor(
      popup = popup,
      inner = inner,
      box = box,
      // The line rect starts at x=0 of a space whose origin is the content's left edge.
      expectedLeft = box.left + (box.right - inner.right),
    )
  }

  /**
   * Dismissal runs through `Popup.onDismissRequest`, and a BACK press drives it the same way an
   * outside tap does. An outside *tap* cannot be faked here: `performTouchInput` injects
   * Compose-level pointer events, but the popup lives in its own window whose outside-press
   * listener answers real MotionEvents only - Maestro proves that half on the device.
   */
  @Test
  fun pressingBackDismissesTheDetail() {
    val state = setContent()
    focusField()

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideRangePx(), rowMiddlePx(line = 2))) }
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsDisplayed()
    // The field's own tap lands after the card is up (it waits out a double tap), and takes the
    // window's focus with it; a BACK sent before that goes to the field's keyboard, not the card.
    compose.waitUntil(CARET_TIMEOUT_MS) { state.selection.start in RANGE_START..RANGE_END }
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  /** On the armed, diagnosed line: the dot's place removes the breakpoint, the range's place reads the finding. */
  private fun bothTargetsAt(width: Dp) {
    var tapped: Int? = null
    setContent(
      width = width,
      breakpoints = listOf(Breakpoint(line = 2)),
      onGutterTap = { tapped = it },
    )

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(oldMarkPx(), rowMiddlePx(line = 2))) }
    assertEquals(2, tapped)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()

    tapped = null
    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideRangePx(), rowMiddlePx(line = 2))) }
    assertNull(tapped)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsDisplayed()
  }

  /** The field takes a tap to place the caret only once it holds focus, as a person's second tap does. */
  private fun focusField() {
    compose.onNodeWithText(FIRST_LINE, substring = true)
      .performSemanticsAction(SemanticsActions.RequestFocus)
    compose.waitForIdle()
  }

  private fun setContent(
    width: Dp = FIELD_WIDTH,
    text: String = TWO_LINES,
    breakpoints: List<Breakpoint> = emptyList(),
    diagnostics: List<CellDiagnostic> = DIAGNOSTICS,
    onGutterTap: (Int) -> Unit = {},
    onGutterLongPress: (Int) -> Unit = {},
    boxed: Boolean = false,
  ): TextFieldState {
    val state = TextFieldState(text)
    compose.setContent {
      JupyterDroidTheme {
        // The tag sits on the *wrapper*, not the field: the field's own semantics bounds start
        // where the code does - past the start padding the gutter occupies - while `detectGutterTap`'s
        // `down.position.x` is measured from the element's own left edge, one gutter width earlier.
        // Clicks dispatched on the field node therefore land one gutter width right of where they
        // aimed and miss the gutter entirely; on the wrapper both spaces share one origin and an
        // offset here is the offset the gesture sees (found live on the tablet).
        Box(modifier = Modifier.width(width).testTag(FIELD_TAG)) {
          CodeCellField(
            state = state,
            onSourceEdited = {},
            modifier = if (boxed) Modifier.fillMaxWidth().cellEditorBox(editing = true) else Modifier,
            breakpoints = breakpoints,
            diagnostics = diagnostics,
            onGutterTap = onGutterTap,
            onGutterLongPress = onGutterLongPress,
          )
        }
      }
    }
    return state
  }

  /** The row's own height - the wrapper is the field's height, and the fixture is two lines. */
  private fun rowHeightPx(): Float = with(compose.density) {
    val box = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot()
    (box.bottom - box.top).toPx() / 2f
  }

  /** The middle of [line]'s row - one-based, as the gesture reports it. */
  private fun rowMiddlePx(line: Int) = rowHeightPx() * (line - 0.5f)

  /** Where the old gutter mark was drawn - one row tall at the gutter's left edge, under the breakpoint's dot. */
  private fun oldMarkPx() = rowHeightPx() / 2f

  /** Where the code starts - the editable text's left edge, one gutter width into the wrapper. */
  private fun gutterWidthPx(): Float {
    val box = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot()
    val text = compose.onNodeWithText(FIRST_LINE, substring = true).getUnclippedBoundsInRoot()
    return with(compose.density) { (text.left - box.left).toPx() }
  }

  /** Past the mark column but still inside the gutter - on the numbers themselves. */
  private fun insideNumbersPx() = (rowHeightPx() + gutterWidthPx()) / 2f

  /** Inside `never_defined` - six characters of `print(` and a few more into the name. */
  private fun insideRangePx() = gutterWidthPx() + with(compose.density) { INSIDE_RANGE.toPx() }

  private companion object {
    /** `DiagnosticDetailPopup`'s own accessibility marker - the way "the popup is open" is asked. */
    const val DETAIL_DESCRIPTION = "Diagnostic detail"
    const val FIRST_LINE = "print(1)"
    const val TWO_LINES = "$FIRST_LINE\nprint(never_defined)"
    const val FIELD_TAG = "diagnostic-mark-field"
    const val RANGE_START = 15
    const val RANGE_END = 28
    const val CARET_TIMEOUT_MS = 3_000L
    val FIELD_WIDTH = 300.dp

    /** Six characters of `print(` at 8.25dp are 49.5dp; 100dp is well inside the name's 107dp. */
    val INSIDE_RANGE = 100.dp

    /** Past the hint's 16.5dp span, inside the 48dp a finger gets (centred on it: 32dp to the right). */
    val HINT_BESIDE = 28.dp
    val DIAGNOSTICS = listOf(
      CellDiagnostic(
        line = 2,
        start = RANGE_START,
        end = RANGE_END,
        severity = SEVERITY_ERROR,
        code = "E501",
        source = "pyflakes",
        message = "undefined name 'never_defined'",
      ),
    )
    val HINT = CellDiagnostic(
      line = 1,
      start = 0,
      end = 1,
      severity = SEVERITY_HINT,
      code = null,
      source = "pylsp",
      message = "consider a docstring",
    )
  }
}
