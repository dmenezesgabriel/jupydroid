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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P6a-6's own gate: a tap in the gutter arms or disarms that line - no kernel behind it yet.
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
    setContent(CODE) { line -> tapped = line }

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideGutterPx(), height / 2f)) }

    assertEquals(1, tapped)
  }

  @Test
  fun tappingTheSecondLineOfTheGutterReportsLineTwo() {
    var tapped: Int? = null
    setContent(TWO_LINES) { line -> tapped = line }

    compose.onNodeWithTag(FIELD_TAG).performTouchInput {
      click(Offset(insideGutterPx(), height * 0.75f))
    }

    assertEquals(2, tapped)
  }

  @Test
  fun tappingTheCodeItselfDoesNotArmABreakpoint() {
    var tapped: Int? = null
    setContent(CODE) { line -> tapped = line }

    compose.onNodeWithTag(FIELD_TAG).performTouchInput { click(Offset(insideCodePx(), height / 2f)) }

    assertEquals(null, tapped)
  }

  private fun setContent(text: String, onGutterTap: (Int) -> Unit) {
    compose.setContent {
      JupyterDroidTheme {
        Box(modifier = Modifier.width(FIELD_WIDTH)) {
          CodeCellField(
            state = rememberTextFieldState(text),
            onSourceEdited = {},
            onGutterTap = onGutterTap,
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
