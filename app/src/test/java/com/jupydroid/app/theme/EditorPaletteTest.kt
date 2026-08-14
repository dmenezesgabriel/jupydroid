package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color
import com.jupydroid.app.syntax.SyntaxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palettes, held to what a phone needs rather than to what a desktop got away with.
 *
 * This is the regression test for the contrast defect recorded in `docs/DECISIONS.md`: syntax colours
 * used to be `MaterialTheme.colorScheme` roles, so `NUMBER` landed on `secondary` and sat almost
 * on top of the body text in dark. Deriving colour from a role can never be asserted, because the
 * role is whatever the wallpaper made it - which is why the fix is a palette and why the palette
 * gets a contrast floor.
 */
class EditorPaletteTest {
  private val palettes = mapOf("dark" to DarkEditorPalette, "light" to LightEditorPalette)

  @Test
  fun everySyntaxKindButPlainIsStyled() {
    palettes.forEach { (name, palette) ->
      val styled = palette.syntax.styles().keys
      val expected = SyntaxKind.entries.toSet() - SyntaxKind.PLAIN
      assertEquals("$name must style every kind but PLAIN", expected, styled)
    }
  }

  /**
   * PLAIN is absent on purpose and must stay absent: unstyled text already draws in the field's
   * own colour, so a span for it would be one that changes nothing.
   */
  @Test
  fun plainIsNeverStyled() {
    palettes.forEach { (name, palette) ->
      assertEquals("$name must not style PLAIN", null, palette.syntax.styles()[SyntaxKind.PLAIN])
    }
  }

  /**
   * A span that paints text the colour it already had is a span that does nothing, and that is
   * the *shape* of the defect this file exists for - `NUMBER` on `colorScheme.secondary` was not
   * invisible, it was indistinguishable from the text around it.
   */
  @Test
  fun noSyntaxColourIsTheBodyTextColour() {
    palettes.forEach { (name, palette) ->
      palette.syntax.styles().forEach { (kind, style) ->
        assertNotEquals("$name $kind is the same colour as unstyled text", palette.syntax.plain, style.color)
      }
    }
  }

  /**
   * The five kinds Python emits appear in one cell together, so they have to be distinguishable
   * from each other. Markdown's do not get the same assertion, and deliberately: IntelliJ maps
   * `MARKDOWN_CODE_SPAN` and `MARKDOWN_BLOCK_QUOTE` both onto `DEFAULT_STRING`
   * (`MarkdownHighlighterColors.java:24-27`), so a uniqueness rule over every kind would be a
   * rule the reference itself breaks.
   */
  @Test
  fun theKindsPythonEmitsAreMutuallyDistinct() {
    palettes.forEach { (name, palette) ->
      val colours = PYTHON_KINDS.map { palette.syntax.styles().getValue(it).color }
      assertEquals("$name has duplicate Python syntax colours", colours.size, colours.toSet().size)
    }
  }

  /**
   * WCAG 2.1 AA is 4.5:1 for body text, and code in a cell is body text.
   *
   * [SyntaxKind.COMMENT] is held to 3:1 instead, and that is a decision rather than an oversight:
   * a comment is meant to recede, IntelliJ's own value sits there in both schemes
   * (`#7A7E85` dark, `#8C8C8C` light - measured 3.39:1 and 3.16:1), and dragging it up to 4.5
   * would make comments shout as loudly as the code they annotate.
   */
  @Test
  fun everyColouredKindClearsItsContrastFloor() {
    palettes.forEach { (name, palette) ->
      palette.syntax.styles().forEach { (kind, style) ->
        val colour = style.color
        if (colour == Color.Unspecified) return@forEach
        val floor = if (kind == SyntaxKind.COMMENT) COMMENT_FLOOR else BODY_TEXT_FLOOR
        val ratio = contrastRatio(colour, palette.cellBackground)
        assertTrue(
          "$name $kind is ${"%.2f".format(ratio)}:1 on the cell background, below $floor:1",
          ratio >= floor,
        )
      }
    }
  }

  /**
   * Emphasis carries no colour in IntelliJ - `MARKDOWN_BOLD` and `MARKDOWN_ITALIC` are declared
   * with no base attributes at all (`MarkdownHighlighterColors.java:20-23`), so they are font
   * style and nothing else. Asserted because the obvious mistake is to give them a colour to
   * make the map look complete.
   */
  @Test
  fun emphasisIsWeightAndSlantRatherThanColour() {
    palettes.forEach { (name, palette) ->
      val styles = palette.syntax.styles()
      assertEquals("$name STRONG must not be coloured", Color.Unspecified, styles.getValue(SyntaxKind.STRONG).color)
      assertEquals("$name EMPHASIS must not be coloured", Color.Unspecified, styles.getValue(SyntaxKind.EMPHASIS).color)
      assertNotEquals("$name STRONG must be bold", null, styles.getValue(SyntaxKind.STRONG).fontWeight)
      assertNotEquals("$name EMPHASIS must be italic", null, styles.getValue(SyntaxKind.EMPHASIS).fontStyle)
    }
  }

  /** A cell has to be visible *as a cell*, which it is not when it matches the page behind it. */
  @Test
  fun theCellSitsOnADifferentSurfaceFromTheEditor() {
    palettes.forEach { (name, palette) ->
      assertNotEquals("$name cell and editor backgrounds must differ", palette.editorBackground, palette.cellBackground)
    }
  }

  @Test
  fun theTwoPalettesAreNotTheSamePalette() {
    assertNotEquals(DarkEditorPalette.editorBackground, LightEditorPalette.editorBackground)
    assertNotEquals(DarkEditorPalette.syntax.keyword, LightEditorPalette.syntax.keyword)
  }

  private companion object {
    const val BODY_TEXT_FLOOR = 4.5
    const val COMMENT_FLOOR = 3.0

    val PYTHON_KINDS = listOf(
      SyntaxKind.KEYWORD,
      SyntaxKind.STRING,
      SyntaxKind.NUMBER,
      SyntaxKind.COMMENT,
      SyntaxKind.FUNCTION,
    )
  }
}

/**
 * WCAG 2.1 relative-luminance contrast, `(lighter + 0.05) / (darker + 0.05)`.
 *
 * Test-only: nothing in the app computes contrast at runtime, it only has to be true of the
 * constants. Kept here rather than in `main` so it cannot quietly become production code.
 */
internal fun contrastRatio(a: Color, b: Color): Double {
  val (high, low) = listOf(relativeLuminance(a), relativeLuminance(b)).sortedDescending()
  return (high + 0.05) / (low + 0.05)
}

private fun relativeLuminance(color: Color): Double =
  0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

private fun linear(channel: Float): Double {
  val c = channel.toDouble()
  return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}
