package com.jupydroid.app.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A glyph beside text is its stated size at the default font scale and keeps its proportion to the text's
 * own size beyond it.
 *
 * A JVM `Density` converts `sp` linearly, so the curve Android 14 applies is stood in for by
 * [ConcaveFontDensity]: it grows a small size more than a large one, which is the shape that made a 24dp glyph
 * beside a 14sp label fall behind when it was converted from its own size. That the glyph follows the label's
 * real curve is `MoreRailFontScaleTest`'s and `MainAreaTabFontScaleTest`'s to state on a device.
 */
class GlyphScaleTest {
  private val glyph = 24.dp
  private val label = 14.sp

  @Test
  fun aGlyphKeepsItsStatedSizeAtTheDefaultFontScale() {
    assertEquals(24f, Density(density = 2f, fontScale = 1f).glyphBesideText(glyph, label).value, 0.001f)
  }

  @Test
  fun aGlyphKeepsItsRatioToTheLabelWhereTheCurveGrowsALargeSizeLess() {
    val density = ConcaveFontDensity(fontScale = 2f)
    val labelDp = with(density) { label.toDp() }

    val grown = density.glyphBesideText(glyph, label)

    assertEquals(glyph.value / label.value, grown.value / labelDp.value, 0.001f)
    val convertedFromItsOwnSize = with(density) { glyph.value.sp.toDp() }
    assertTrue("$grown is not above $convertedFromItsOwnSize, its own size converted", grown > convertedFromItsOwnSize)
  }

  @Test
  fun aGlyphThatStatesNoLabelIsConvertedFromItsOwnSize() {
    val density = ConcaveFontDensity(fontScale = 2f)

    assertEquals(with(density) { 24.sp.toDp() }.value, density.glyphBesideText(glyph).value, 0.001f)
  }

  @Test
  fun aGlyphDoesNotDependOnTheScreensDensity() {
    val sparse = Density(density = 1f, fontScale = 1.5f).glyphBesideText(glyph, label)
    val dense = Density(density = 3f, fontScale = 1.5f).glyphBesideText(glyph, label)

    assertEquals(sparse.value, dense.value, 0.001f)
  }

  /** A density whose `sp` grows by [fontScale] at 14 and by less for every sp above it, down to 1.0. */
  private class ConcaveFontDensity(override val fontScale: Float) : Density {
    override val density: Float = 1f

    override fun TextUnit.toDp(): Dp {
      val factor = fontScale - (fontScale - 1f) * ((value - 14f) / 40f).coerceIn(0f, 1f)
      return (value * factor).dp
    }
  }
}
