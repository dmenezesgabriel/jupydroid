package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `notebook:toggle-all-cell-line-numbers` reaches **all three** cell types, which is what the
 * command is: `toggleAllLineNumbers` rewrites `code`, `markdown` and `raw` together and reads them
 * back as a conjunction (`packages/notebook/src/actions.tsx:1792-1812`). One field honouring the
 * flag while another ignores it is the regression worth a test.
 *
 * **What is asserted is the room the gutter takes, not the digits in it.** The numbers are drawn
 * on a canvas so they carry no semantics node - deliberately, since a line number must never be
 * selectable with the code - and the `gutter-wrapped-line` flow says so. What *is* readable
 * is where the text starts: [lineNumbers] ends in `padding(start = width)` and the field's own
 * node sits inside that padding, so the gutter's presence is the field's left edge moving, which
 * is exactly what a user sees.
 */
@RunWith(AndroidJUnit4::class)
class LineNumberGutterTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aCodeCellIndentsForItsGutterAndGivesTheRoomBackWhenHidden() {
    assertGutterIsReservedOnlyWhenShown(CODE) { shown ->
      CodeCellField(
        state = rememberTextFieldState(CODE),
        onSourceEdited = {},
        lineNumbersShown = shown,
      )
    }
  }

  @Test
  fun aMarkdownCellIndentsForItsGutterAndGivesTheRoomBackWhenHidden() {
    assertGutterIsReservedOnlyWhenShown(PROSE) { shown ->
      MarkdownCellField(
        state = rememberTextFieldState(PROSE),
        onSourceEdited = {},
        lineNumbersShown = shown,
      )
    }
  }

  @Test
  fun aRawCellIndentsForItsGutterAndGivesTheRoomBackWhenHidden() {
    assertGutterIsReservedOnlyWhenShown(RAW) { shown ->
      RawCellField(
        state = rememberTextFieldState(RAW),
        onSourceEdited = {},
        lineNumbersShown = shown,
      )
    }
  }

  /**
   * Relative rather than absolute: the gutter's width comes from measuring digits in the cell's
   * own font, so a test naming a dp value would be asserting the font metrics of whichever device
   * ran it. That the text moves is the claim; how far is [gutterWidth]'s business.
   */
  private fun assertGutterIsReservedOnlyWhenShown(text: String, field: @Composable (Boolean) -> Unit) {
    var shown by mutableStateOf(true)
    compose.setContent {
      JupyterDroidTheme {
        Box(modifier = Modifier.width(FIELD_WIDTH)) { field(shown) }
      }
    }

    val withGutter = compose.onNodeWithText(text).getUnclippedBoundsInRoot().left
    shown = false
    compose.waitForIdle()
    val withoutGutter = compose.onNodeWithText(text).getUnclippedBoundsInRoot().left

    assertTrue(
      "expected the text to start further right with the gutter shown, " +
        "got $withGutter shown against $withoutGutter hidden",
      withGutter > withoutGutter,
    )
  }

  private companion object {
    const val CODE = "print(1)"
    const val PROSE = "some prose"
    const val RAW = "\\begin{equation}"
    val FIELD_WIDTH = 300.dp
  }
}
