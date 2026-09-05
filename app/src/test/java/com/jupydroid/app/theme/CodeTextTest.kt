package com.jupydroid.app.theme

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Code is drawn at one size, and the line height under it is the reference's own ratio.
 *
 * Written from a measurement rather than a preference. Before this there were **four sizes for one
 * thing**: the cell's source stated none at all - `codeSourceStyle` reads `LocalTextStyle`, whose
 * Material default is `TextStyle.Default`, so an unspecified `fontSize` resolved to Compose's
 * `DefaultFontSize = 14.sp` (`ui-text`, `SpanStyle.kt:48`) with the font's own line height -
 * while outputs and the folded-cell placeholder were `bodySmall` (12sp) and the completion popup
 * `bodyMedium` (14sp). JupyterLab has one `--jp-code-font-size` and one `--jp-code-line-height`
 * for all of them (`packages/theme-light-extension/style/variables.css:196-197`).
 */
class CodeTextTest {
  /**
   * The invariant that matters, and the reason both numbers are not simply asserted: the line
   * height is *derived*, so changing the size without the line height fails here rather than
   * shipping a code cell whose lines drift apart.
   */
  @Test
  fun theLineHeightIsTheReferencesRatioOverTheSize() {
    val expected = (CodeText.size.value * REFERENCE_LINE_HEIGHT_RATIO).roundToInt()

    assertEquals("$REFERENCE_LINE_HEIGHT_RATIO x ${CodeText.size}", expected, CodeText.lineHeight.value.toInt())
  }

  /**
   * [CodeText.lineHeightFor] at a size other than [CodeText.size] - the seam the terminal's own
   * font-size setting (`docs/PLAN.md` P4j-3c) resizes glyphs through, so a second, undertested copy
   * of the ratio is not what proves it.
   */
  @Test
  fun lineHeightForAnotherSizeUsesTheSameRatio() {
    val size = 20.sp
    val expected = (size.value * REFERENCE_LINE_HEIGHT_RATIO).roundToInt()

    assertEquals("$REFERENCE_LINE_HEIGHT_RATIO x $size", expected, CodeText.lineHeightFor(size).value.toInt())
  }

  /** A fractional sp is a number nobody chose; every Material line height is whole. */
  @Test
  fun bothAreWholeSp() {
    assertEquals(CodeText.size.value.toInt().toFloat(), CodeText.size.value)
    assertEquals(CodeText.lineHeight.value.toInt().toFloat(), CodeText.lineHeight.value)
  }

  /**
   * **The size is on Material's body scale, and that is the whole of why it is not 13.**
   * `--jp-code-font-size: 13px` is a CSS pixel on a desktop; sp is neither, and transcribing the
   * number would have shrunk the one surface - the cell's source - that was already right, to land
   * on a size Material does not name. 14sp is `BodyMediumSize`, and it is what the source resolves
   * to today, so what this step moves is everything else onto it.
   */
  @Test
  fun theSizeIsOneMaterialAlreadyNames() {
    val materialBodySizes = listOf(12f, 14f, 16f)

    assertTrue("${CodeText.size} is not one of $materialBodySizes", CodeText.size.value in materialBodySizes)
  }

  private companion object {
    /** `--jp-code-line-height: 1.3077; /* 17px for 13px base */`. */
    const val REFERENCE_LINE_HEIGHT_RATIO = 1.3077f
  }
}
