package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `wrapEnabled` has to actually change how a long line lays out, not
 * merely compile - [CodeCellFieldFontSizeTest]'s own precedent for proving a layout parameter
 * against the field's own [TextLayoutResult] rather than assuming.
 */
@RunWith(AndroidJUnit4::class)
class CodeCellFieldWrapTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun wrapDisabledKeepsALongLineOnOneVisualRowWhileWrapEnabledBreaksIt() {
    var wrappedLayout: TextLayoutResult? = null
    var unwrappedLayout: TextLayoutResult? = null

    compose.setContent {
      JupydroidTheme {
        Box(Modifier.width(BOX_WIDTH)) {
          CodeCellField(
            state = rememberTextFieldState(LONG_LINE),
            onSourceEdited = {},
            wrapEnabled = true,
            onLayoutMeasured = { wrappedLayout = it },
          )
        }
      }
      JupydroidTheme {
        Box(Modifier.width(BOX_WIDTH)) {
          CodeCellField(
            state = rememberTextFieldState(LONG_LINE),
            onSourceEdited = {},
            wrapEnabled = false,
            onLayoutMeasured = { unwrappedLayout = it },
          )
        }
      }
    }
    compose.waitForIdle()

    val wrapped = requireNotNull(wrappedLayout) { "the wrapped onLayoutMeasured was never called" }
    val unwrapped = requireNotNull(unwrappedLayout) { "the unwrapped onLayoutMeasured was never called" }
    assertTrue("wrap enabled should break the long line onto more than one visual row", wrapped.lineCount > 1)
    assertEquals("wrap disabled should keep the long line on a single visual row", 1, unwrapped.lineCount)
  }

  private companion object {
    const val LONG_LINE = "x = 1  # a padded comment written long enough to force wrapping at a narrow width"
    val BOX_WIDTH = 120.dp
  }
}
