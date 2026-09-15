package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `docs/PLAN.md`'s P6c-4: `codeFontSizeSp` has to actually resize what is drawn, not merely
 * compile - the same claim [com.jupydroid.app.TerminalCanvasTest.aLargerTextStyleMeasuresAWiderCell]
 * measures for the terminal's own field. `onLayoutMeasured` is [CodeCellField]'s own hook for
 * exactly this - `FileEditorScreen` already reads it to scroll a search match into view.
 */
@RunWith(AndroidJUnit4::class)
class CodeCellFieldFontSizeTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aLargerCodeFontSizeMeasuresAWiderLine() {
    var smallLayout: TextLayoutResult? = null
    var largeLayout: TextLayoutResult? = null

    compose.setContent {
      JupyterDroidTheme {
        CodeCellField(
          state = rememberTextFieldState(CODE),
          onSourceEdited = {},
          codeFontSizeSp = 10,
          onLayoutMeasured = { smallLayout = it },
        )
      }
      JupyterDroidTheme {
        CodeCellField(
          state = rememberTextFieldState(CODE),
          onSourceEdited = {},
          codeFontSizeSp = 30,
          onLayoutMeasured = { largeLayout = it },
        )
      }
    }
    compose.waitForIdle()

    val small = requireNotNull(smallLayout) { "the 10sp onLayoutMeasured was never called" }
    val large = requireNotNull(largeLayout) { "the 30sp onLayoutMeasured was never called" }
    val smallWidth = small.getLineRight(0)
    val largeWidth = large.getLineRight(0)
    assertTrue("a 30sp line $largeWidth should be wider than a 10sp line $smallWidth", largeWidth > smallWidth)
  }

  private companion object {
    const val CODE = "import numpy"
  }
}
