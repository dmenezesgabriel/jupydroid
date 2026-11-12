package com.jupydroid.app.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A glyph beside text is its stated size at the default font scale and grows with the font beyond it.
 *
 * A JVM `Density` converts `sp` linearly, so the bound here is loose on purpose. Android 14's curve is
 * not linear, and that the glyph follows the *label's* curve is `MainAreaTabFontScaleTest`'s to state on a device.
 */
class GlyphScaleTest {
  private val glyph = 14.dp

  @Test
  fun aGlyphKeepsItsStatedSizeAtTheDefaultFontScale() {
    assertEquals(14f, Density(density = 2f, fontScale = 1f).glyphBesideText(glyph).value, 0.001f)
  }

  @Test
  fun aGlyphGrowsAtTwiceTheFontScaleButNotBeyondTwice() {
    val grown = Density(density = 2f, fontScale = 2f).glyphBesideText(glyph).value

    assertTrue("14dp at font scale 2.0 is $grown", grown > 14f && grown <= 28.001f)
  }

  @Test
  fun aGlyphDoesNotDependOnTheScreensDensity() {
    val sparse = Density(density = 1f, fontScale = 1.5f).glyphBesideText(glyph)
    val dense = Density(density = 3f, fontScale = 1.5f).glyphBesideText(glyph)

    assertEquals(sparse.value, dense.value, 0.001f)
  }
}
