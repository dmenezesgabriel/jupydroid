package com.jupydroid.app.ui.editor

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A press on a diagnosed line's **mark column** shows the findings VS Code
 * answers on the marker's hover - the numbers of that same line still toggle a breakpoint.
 *
 * The mark itself is canvas, not a semantics node (`BreakpointGutterTapTest` records the same
 * drawBehind limitation), so what is asserted is the tap's *effect*: the popup's text, or the
 * line the breakpoint callback heard.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticMarkTapTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun tappingTheMarkOfADiagnosedLineShowsItsFindings() {
    setContent()

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      click(Offset(insideMarkPx(), rowMiddlePx(line = 2)))
    }

    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsDisplayed()
    compose.onNodeWithText("error: undefined name 'never_defined'", substring = true).assertIsDisplayed()
    compose.onNodeWithText("pyflakes", substring = true).assertIsDisplayed()
    compose.onNodeWithText("E501", substring = true).assertIsDisplayed()
  }

  @Test
  fun tappingTheNumbersOfADiagnosedLineStillTogglesABreakpoint() {
    var tapped: Int? = null
    setContent(onGutterTap = { tapped = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      click(Offset(insideNumbersPx(), rowMiddlePx(line = 2)))
    }

    assertEquals(2, tapped)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  @Test
  fun tappingTheMarkOfACleanLineStillTogglesABreakpoint() {
    var tapped: Int? = null
    setContent(onGutterTap = { tapped = it })

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      click(Offset(insideMarkPx(), rowMiddlePx(line = 1)))
    }

    assertEquals(1, tapped)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  /** The condition editor outranks the mark: armed and held opens it, even on the mark. */
  @Test
  fun longPressingAnArmedDiagnosedLinesMarkStillOpensTheConditionEditor() {
    var longPressed: Int? = null
    setContent(
      breakpoints = listOf(Breakpoint(line = 2)),
      onGutterLongPress = { longPressed = it },
    )

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      longClick(Offset(insideMarkPx(), rowMiddlePx(line = 2)))
    }

    assertEquals(2, longPressed)
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  /**
   * Dismissal runs through `Popup.onDismissRequest`, and a BACK press drives it the same way an
   * outside tap does. An outside *tap* cannot be faked here: `performTouchInput` injects
   * Compose-level pointer events, but the popup lives in its own window whose outside-press
   * listener answers real MotionEvents only - Maestro proves that half on the device.
   */
  @Test
  fun pressingBackDismissesTheDetail() {
    setContent()

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      click(Offset(insideMarkPx(), rowMiddlePx(line = 2)))
    }
    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsDisplayed()
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    compose.onNodeWithContentDescription(DETAIL_DESCRIPTION).assertIsNotDisplayed()
  }

  private fun setContent(
    text: String = TWO_LINES,
    breakpoints: List<Breakpoint> = emptyList(),
    diagnostics: List<CellDiagnostic> = DIAGNOSTICS,
    onGutterTap: (Int) -> Unit = {},
    onGutterLongPress: (Int) -> Unit = {},
  ) {
    compose.setContent {
      JupyterDroidTheme {
        // The tag sits on the *wrapper*, not the field: the field's own semantics bounds start
        // where the code does - past the start padding the gutter occupies - while `detectGutterTap`'s
        // `down.position.x` is measured from the element's own left edge, one gutter width earlier.
        // Clicks dispatched on the field node therefore land one gutter width right of where they
        // aimed and miss the gutter entirely; on the wrapper both spaces share one origin and an
        // offset here is the offset the gesture sees (found live on the tablet).
        Box(modifier = Modifier.width(FIELD_WIDTH).testTag(FIELD_TAG)) {
          CodeCellField(
            state = rememberTextFieldState(text),
            onSourceEdited = {},
            breakpoints = breakpoints,
            diagnostics = diagnostics,
            onGutterTap = onGutterTap,
            onGutterLongPress = onGutterLongPress,
          )
        }
      }
    }
  }

  /** The row's own height - the wrapper is the field's height, and the fixture is two lines. */
  private fun rowHeightPx(): Float = with(compose.density) {
    val box = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot()
    (box.bottom - box.top).toPx() / 2f
  }

  /** The middle of [line]'s row - one-based, as the gesture reports it. */
  private fun rowMiddlePx(line: Int) = rowHeightPx() * (line - 0.5f)

  /** Inside the mark column - one row tall at the gutter's left edge. */
  private fun insideMarkPx() = rowHeightPx() / 4f

  /** Where the code starts - the editable text's left edge, one gutter width into the wrapper. */
  private fun gutterWidthPx(): Float {
    val box = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot()
    val text = compose.onNodeWithText(FIRST_LINE, substring = true).getUnclippedBoundsInRoot()
    return with(compose.density) { (text.left - box.left).toPx() }
  }

  /** Past the mark column but still inside the gutter - on the numbers themselves. */
  private fun insideNumbersPx() = (rowHeightPx() + gutterWidthPx()) / 2f

  private companion object {
    /** `DiagnosticDetailPopup`'s own accessibility marker - the way "the popup is open" is asked. */
    const val DETAIL_DESCRIPTION = "Diagnostic detail"
    const val FIRST_LINE = "print(1)"
    const val TWO_LINES = "$FIRST_LINE\nprint(never_defined)"
    const val FIELD_TAG = "diagnostic-mark-field"
    val FIELD_WIDTH = 300.dp
    val DIAGNOSTICS = listOf(
      CellDiagnostic(
        line = 2,
        start = 15,
        end = 28,
        severity = SEVERITY_ERROR,
        code = "E501",
        source = "pyflakes",
        message = "undefined name 'never_defined'",
      ),
    )
  }
}
