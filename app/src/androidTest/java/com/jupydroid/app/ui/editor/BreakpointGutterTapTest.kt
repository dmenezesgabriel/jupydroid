package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tap in the gutter arms or disarms that line - no kernel behind it.
 *
 * The marker is drawn on the same canvas as the numbers it replaces (`LineNumberGutterTest`
 * already records why they carry no semantics node), so what is asserted here is the tap's
 * *effect* - the line it reports - the same "assert the effect, not the canvas" shape
 * `LineNumberGutterTest`/`FileEditorRevealTest` use for the same drawBehind limitation.
 */
@RunWith(AndroidJUnit4::class)
class BreakpointGutterTapTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun tappingTheGutterOfASingleLineCellReportsLineOne() {
    var tapped: Int? = null
    setContent(CODE, onGutterTap = { line -> tapped = line })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideGutterPx(), height / 2f)) }

    assertEquals(1, tapped)
  }

  @Test
  fun tappingTheSecondLineOfTheGutterReportsLineTwo() {
    var tapped: Int? = null
    setContent(TWO_LINES, onGutterTap = { line -> tapped = line })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      click(Offset(insideGutterPx(), height * 0.75f))
    }

    assertEquals(2, tapped)
  }

  @Test
  fun tappingTheCodeItselfDoesNotArmABreakpoint() {
    var tapped: Int? = null
    setContent(CODE, onGutterTap = { line -> tapped = line })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideCodePx(), height / 2f)) }

    assertEquals(null, tapped)
  }

  /**
   * A long-press on an *armed* line's gutter is a distinct signal from an
   * ordinary tap - it opens the condition editor rather than toggling the breakpoint a second time.
   */
  @Test
  fun longPressingAnArmedLinesGutterReportsThatLine() {
    var longPressed: Int? = null
    setContent(CODE, breakpoints = listOf(Breakpoint(line = 1)), onGutterLongPress = { longPressed = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { longClick(Offset(insideGutterPx(), height / 2f)) }

    assertEquals(1, longPressed)
  }

  /**
   * The regression this row's own gate found live on the tablet: an eager `onTap` fired on every
   * press used to toggle the very breakpoint a long-press was adding a condition to, off, in the
   * same gesture - the dot vanished under every hold and the dialog opened for a breakpoint that
   * no longer existed. A long-press on an armed line must fire `onGutterLongPress` alone.
   */
  @Test
  fun longPressingAnArmedLinesGutterDoesNotAlsoToggleIt() {
    var tapped: Int? = null
    setContent(CODE, breakpoints = listOf(Breakpoint(line = 1)), onGutterTap = { tapped = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { longClick(Offset(insideGutterPx(), height / 2f)) }

    assertEquals(null, tapped)
  }

  /** Nothing to attach a condition to on a line with no breakpoint - the long-press is a no-op. */
  @Test
  fun longPressingAnUnarmedLinesGutterReportsNothing() {
    var longPressed: Int? = null
    setContent(CODE, onGutterLongPress = { longPressed = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { longClick(Offset(insideGutterPx(), height / 2f)) }

    assertEquals(null, longPressed)
  }

  /** A long hold on a bare line is still an ordinary tap - it arms the line, same as a quick one. */
  @Test
  fun longPressingAnUnarmedLinesGutterStillArmsIt() {
    var tapped: Int? = null
    setContent(CODE, onGutterTap = { tapped = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { longClick(Offset(insideGutterPx(), height / 2f)) }

    assertEquals(1, tapped)
  }

  /** A quick tap toggles the breakpoint (already covered above) and never also reports a long-press. */
  @Test
  fun aQuickTapDoesNotReportALongPress() {
    var longPressed: Int? = null
    setContent(CODE, breakpoints = listOf(Breakpoint(line = 1)), onGutterLongPress = { longPressed = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideGutterPx(), height / 2f)) }

    assertEquals(null, longPressed)
  }

  private fun setContent(
    text: String,
    breakpoints: List<Breakpoint> = emptyList(),
    onGutterTap: (Int) -> Unit = {},
    onGutterLongPress: (Int) -> Unit = {},
  ) {
    compose.setContent {
      JupydroidTheme {
        Box(modifier = Modifier.width(FIELD_WIDTH)) {
          CodeCellField(
            state = rememberTextFieldState(text),
            onSourceEdited = {},
            breakpoints = breakpoints,
            onGutterTap = onGutterTap,
            onGutterLongPress = onGutterLongPress,
            modifier = Modifier.testTag(FIELD_TAG),
          )
        }
      }
    }
  }

  /** Halfway between the field's own left edge and where the code text starts - inside the gutter. */
  private fun insideGutterPx(): Float {
    val field = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot()
    val text = compose.onNodeWithText(FIRST_LINE, substring = true).getUnclippedBoundsInRoot()
    val gutterWidth = text.left - field.left
    return with(compose.density) { (gutterWidth / 2).toPx() }
  }

  /** Comfortably inside the code, past the gutter's right edge. */
  private fun insideCodePx(): Float {
    val field = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot()
    val text = compose.onNodeWithText(FIRST_LINE, substring = true).getUnclippedBoundsInRoot()
    return with(compose.density) { (text.left - field.left + 4.dp).toPx() }
  }

  private companion object {
    const val FIRST_LINE = "print(1)"
    const val CODE = FIRST_LINE
    const val TWO_LINES = "$FIRST_LINE\nprint(2)"
    const val FIELD_TAG = "breakpoint-gutter-field"
    val FIELD_WIDTH = 300.dp
  }
}
