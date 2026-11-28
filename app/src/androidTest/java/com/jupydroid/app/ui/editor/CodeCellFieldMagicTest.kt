package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.DarkEditorPalette
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * A field whose caller says nothing paints a `%`-line and a `%%`-cell as IPython, which is what the
 * notebook's cells and the console's input take the default for - `FileEditorScreen` and
 * `SpaceSettingsDialog` keep their explicit highlighters.
 *
 * Read off the pixels, since a colour has no semantics node
 * (`SpaceSettingsDialogStartupScriptTest`'s finding) and the gutter's digits are canvas ink too.
 */
@RunWith(AndroidJUnit4::class)
class CodeCellFieldMagicTest {
  @get:Rule val compose = createComposeRule()

  /**
   * `%matplotlib inline`: the sigil is the magic colour, the name the keyword colour, in that
   * order - under `PythonSyntax` the whole line was plain.
   */
  @Test
  fun aLineMagicPaintsItsSigilAndName() {
    showCell("%matplotlib inline")

    val pixels = capture()
    val sigil = inkColumns(pixels, DarkEditorPalette.syntax.magic)
    val name = inkColumns(pixels, DarkEditorPalette.syntax.keyword)

    assertTrue("no magic-coloured sigil", sigil.isNotEmpty())
    assertTrue("no keyword-coloured name", name.isNotEmpty())
    assertTrue("the sigil does not sit left of the name", sigil.min() < name.min())
  }

  /**
   * `%%bash\necho hi`: `echo` is `FUNCTION` ink only if the body was delegated to
   * `ShellSyntax` - under `PythonSyntax` it is a plain name, so its presence proves the body
   * changed language, not just that the first line lit up.
   */
  @Test
  fun aCellMagicPaintsItsBodyInItsLanguage() {
    showCell("%%bash\necho hi")

    val pixels = capture()
    val sigil = inkColumns(pixels, DarkEditorPalette.syntax.magic)
    val body = inkColumns(pixels, DarkEditorPalette.syntax.function)

    assertTrue("no magic-coloured %%", sigil.isNotEmpty())
    assertTrue("the body's `echo` is not shell-coloured", body.isNotEmpty())
  }

  private fun showCell(source: String) {
    compose.setContent {
      JupydroidTheme(darkTheme = true) {
        Box(Modifier.testTag(CELL_BOX)) {
          CodeCellField(state = rememberTextFieldState(source), onSourceEdited = {})
        }
      }
    }
  }

  private fun capture(): PixelMap = compose.onNodeWithTag(CELL_BOX).captureToImage().toPixelMap()

  /** The x of every pixel within a rounding of [colour]: a glyph's core is its colour, its edges blended. */
  private fun inkColumns(pixels: PixelMap, colour: Color): List<Int> =
    (0 until pixels.width).filter { x -> (0 until pixels.height).any { y -> pixels[x, y].isNear(colour) } }

  private fun Color.isNear(colour: Color): Boolean =
    abs(red - colour.red) + abs(green - colour.green) + abs(blue - colour.blue) < INK_TOLERANCE

  private companion object {
    const val CELL_BOX = "cell-box"
    const val INK_TOLERANCE = 0.03f
  }
}
