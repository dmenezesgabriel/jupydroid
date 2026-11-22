package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.DarkEditorPalette
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The squiggle is canvas, not semantics, so its *ink* is what is asserted: pixels at a
 * diagnosed row's bottom, matched to the palette's own severity colour the way
 * `CellEditorBoxTest` samples the box's edge. The gutter draws no diagnostic mark since the
 * squiggle took over its tap (`DiagnosticMarkTapTest`), so no test here looks for one.
 *
 * The scan regions are the field's own geometry: the text's left edge is where the text
 * node's bounds say it is (the same way `DiagnosticMarkTapTest` derives the gutter), and
 * the wave band is the image's bottom strip, where `getLineBottom` draws the underline.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticSquiggleDrawTest {
  @get:Rule val compose = createComposeRule()

  /** One `setContent` per test - recomposition swaps the findings, the way publishing does. */
  private var diagnostics by mutableStateOf(emptyList<CellDiagnostic>())

  /** Two severities on one line: each underlines its own span, not the other's. */
  @Test
  fun anErrorSpanAndAWarningSpan_underlineTheirOwnRanges() {
    setContent()
    diagnostics = listOf(
      diagnostic(SEVERITY_ERROR, start = 9, end = 15), // "print("
      diagnostic(SEVERITY_WARNING, start = 15, end = 28), // "never_defined"
    )
    compose.waitForIdle()

    val image = capture()
    val band = waveBand(image)
    val errorX = leftmostX(image, band, DarkEditorPalette.diagnosticError)
    val warningX = leftmostX(image, band, DarkEditorPalette.diagnosticWarning)
    assertNotNull(errorX)
    assertNotNull(warningX)
    assertTrue("warning span must paint right of the error span", warningX!! > errorX!!)

    // Nothing on the text rows above the wave - the marks are range marks, not row highlights.
    val aboveWave = Region(textLeftPx(), image.width, 0, band.first)
    assertNull(firstX(image, aboveWave, DarkEditorPalette.diagnosticWarning))
    assertNull(firstX(image, aboveWave, DarkEditorPalette.diagnosticError))
  }

  /**
   * Same range, two severities: the wave spans it, VS Code's `dotdotdot` only its start.
   * The probes are the *added* ink - a capture with the diagnostic against one without -
   * because the hint's own colour composes to the same mid-grey antialiased text carries;
   * differencing the two is the honest way to isolate what the finding painted.
   */
  @Test
  fun aHintMarksOnlyTheStartOfItsSpan_notItsWholeUnderline() {
    setContent(text = PLAIN_LINE)

    diagnostics = emptyList()
    compose.waitForIdle()
    val clean = capture()

    diagnostics = listOf(diagnostic(SEVERITY_ERROR, start = 9, end = 18))
    compose.waitForIdle()
    val wave = capture()
    val waveExtent = addedExtent(wave, clean)

    diagnostics = listOf(diagnostic(SEVERITY_HINT, start = 9, end = 18))
    compose.waitForIdle()
    val hint = capture()
    val dotsExtent = addedExtent(hint, clean)

    assertTrue("the dots themselves must paint", dotsExtent > 0)
    assertTrue("the wave should span far more of the range than the dots", dotsExtent < waveExtent / 2)
  }

  private fun setContent(text: String = TWO_LINES) {
    compose.setContent {
      JupyterDroidTheme(darkTheme = true) {
        // The surface `cellEditorBox` paints in production (`EditorCell`) - without it the
        // translucent hint (`0xB3`) composites over the window's own surface and is invisible.
        Box(modifier = Modifier.width(FIELD_WIDTH).background(DarkEditorPalette.cellBackground).testTag(FIELD_TAG)) {
          CodeCellField(
            state = rememberTextFieldState(text),
            onSourceEdited = {},
            diagnostics = diagnostics,
            onGutterTap = {},
            onGutterLongPress = {},
          )
        }
      }
    }
  }

  private fun capture(): ImageBitmap = compose.onNodeWithTag(FIELD_TAG).captureToImage()

  /**
   * The code's left edge in pixels - the text node's bounds past the gutter, the same
   * measurement `DiagnosticMarkTapTest.gutterWidthPx` makes. Squiggle ink lives at or past
   * it; the gutter sits before it.
   */
  private fun textLeftPx(): Int {
    val box = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot()
    val text = compose.onNodeWithText(FIRST_LINE, substring = true).getUnclippedBoundsInRoot()
    return with(compose.density) { (text.left - box.left).toPx().toInt() }
  }

  /** The bottom strip of the text area - the wave tile sits at the last line's bottom. */
  private fun waveBand(image: ImageBitmap): IntRange = (image.height - WAVE_BAND_PX) until image.height

  /** Scans stop this far short of the image edge - the mark column's outer bound. */
  private fun waveRegion(image: ImageBitmap): Region =
    Region(textLeftPx(), image.width, image.height - WAVE_BAND_PX, image.height)

  /** The leftmost x inside the text-side wave [band] carrying [colour], or null. */
  private fun leftmostX(image: ImageBitmap, band: IntRange, colour: Color): Int? {
    val pixels = image.toPixelMap()
    for (x in textLeftPx() until image.width) {
      if (band.any { y -> near(pixels[x, y], colour) }) return x
    }
    return null
  }

  /** The leftmost x inside [region] carrying [colour], or null. */
  private fun firstX(image: ImageBitmap, region: Region, colour: Color): Int? {
    val pixels = image.toPixelMap()
    for (x in region.x0 until region.x1) {
      if ((region.y0 until region.y1).any { y -> near(pixels[x, y], colour) }) return x
    }
    return null
  }

  /** The horizontal extent [colour] paints inside the text-side wave [band]. */
  private fun colourExtent(image: ImageBitmap, band: IntRange, colour: Color): Int {
    val pixels = image.toPixelMap()
    val xs = (textLeftPx() until image.width).filter { x -> band.any { y -> near(pixels[x, y], colour) } }
    return if (xs.isEmpty()) 0 else xs.last() - xs.first()
  }

  /**
   * The horizontal extent of the ink [withMarks] adds over [clean] in the wave band -
   * the pixels the findings painted, with the unchanged text differenced out.
   */
  private fun addedExtent(withMarks: ImageBitmap, clean: ImageBitmap): Int {
    val marks = withMarks.toPixelMap()
    val bare = clean.toPixelMap()
    val xs = (textLeftPx() until withMarks.width).filter { x ->
      waveBand(withMarks).any { y ->
        val a = marks[x, y]
        val b = bare[x, y]
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > DIFF_EPS
      }
    }
    return if (xs.isEmpty()) 0 else xs.last() - xs.first()
  }

  /**
   * Within a tight channel distance of what [expected] *composites to* over the cell's
   * background - the hint token itself is translucent (`0xB3`), so its dots never read as
   * the raw colour. The tolerance must stay well under the ~0.8 between the error and
   * warning tokens, or the two severities cross-match.
   */
  private fun near(actual: Color, expected: Color): Boolean {
    val over = DarkEditorPalette.cellBackground
    val composited = Color(
      red = expected.red * expected.alpha + over.red * (1 - expected.alpha),
      green = expected.green * expected.alpha + over.green * (1 - expected.alpha),
      blue = expected.blue * expected.alpha + over.blue * (1 - expected.alpha),
    )
    return abs(actual.red - composited.red) +
      abs(actual.green - composited.green) +
      abs(actual.blue - composited.blue) < TOLERANCE
  }

  private fun diagnostic(severity: Int, start: Int, end: Int) = CellDiagnostic(
    line = 2,
    start = start,
    end = end,
    severity = severity,
    code = "E501",
    source = "pyflakes",
    message = "undefined name 'never_defined'",
  )

  /** An axis-aligned pixel region, for the shape probes. */
  private data class Region(val x0: Int, val x1: Int, val y0: Int, val y1: Int)

  private companion object {
    const val FIRST_LINE = "print(1)"
    const val TWO_LINES = "$FIRST_LINE\nprint(never_defined)"

    /** Nothing below the baseline on line 2 - the hint dots' own grey cannot hide in text ink. */
    const val PLAIN_LINE = "$FIRST_LINE\naaaaaaaaa"
    const val FIELD_TAG = "squiggle-field"
    val FIELD_WIDTH = 300.dp

    /** The wave band's scan height - the 3dp tile plus the slack the field leaves under it. */
    const val WAVE_BAND_PX = 10

    /** Under half the error<->warning token distance (~0.8) so severities cannot cross-match. */
    const val TOLERANCE = 0.35f

    /** The channel-sum distance past which two captures count as added ink, not noise. */
    const val DIFF_EPS = 0.15f
  }
}
