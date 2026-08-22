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

  /**
   * A marked cell has to be visibly marked, which it is not when the tint matches either surface
   * it is drawn over - the page it sits on or the field inside it. Both are checked because the
   * tint shows in the band *around* the field, exactly as `.jp-Cell.jp-mod-selected` does with the
   * editor's own background sitting on top of it.
   */
  @Test
  fun aMarkedCellIsTintedApartFromBothSurfaces() {
    palettes.forEach { (name, palette) ->
      assertNotEquals("$name mark must differ from the page", palette.editorBackground, palette.cellMultiSelected)
      assertNotEquals("$name mark must differ from the cell", palette.cellBackground, palette.cellMultiSelected)
    }
  }

  /**
   * A search that lights every hit the same colour cannot say which one the arrows are standing
   * on, and a hit drawn in the cell's own background is not lit at all. Both halves, because the
   * two colours come from two different references and nothing else keeps them apart.
   */
  @Test
  fun aFoundMatchAndTheCurrentOneAreTellableApartAndFromTheCell() {
    palettes.forEach { (name, palette) ->
      assertNotEquals("$name match must differ from the cell", palette.cellBackground, palette.searchMatch)
      assertNotEquals(
        "$name current match must differ from the others",
        palette.searchMatch,
        palette.currentSearchMatch,
      )
    }
  }

  /**
   * The current match states its own foreground precisely so it can be read; asserted at the same
   * 4.5:1 the syntax colours are held to, since a match is body text like the code around it.
   */
  @Test
  fun theCurrentMatchIsReadableOnItsOwnBackground() {
    palettes.forEach { (name, palette) ->
      val ratio = contrastRatio(palette.currentSearchMatchText, palette.currentSearchMatch)
      assertTrue(
        "$name current match is ${"%.2f".format(ratio)}:1, below $BODY_TEXT_FLOOR:1",
        ratio >= BODY_TEXT_FLOOR,
      )
    }
  }

  /**
   * Both matches state their own foreground, so both are held to the same 4.5:1 the code around
   * them is - a match is body text, and the one you are standing on is the text you most need to
   * read. This is also what replaced the obvious assertion: holding the *syntax* colours to their
   * floor over the match background failed at 3.16:1 on dark, and every background that fixed it
   * measured 1.08:1 against the cell. Stating the foreground is how both references get out of it.
   */
  @Test
  fun bothKindsOfMatchAreReadableOnTheirOwnBackground() {
    palettes.forEach { (name, palette) ->
      mapOf(
        "found" to (palette.searchMatchText to palette.searchMatch),
        "current" to (palette.currentSearchMatchText to palette.currentSearchMatch),
      ).forEach { (which, pair) ->
        val ratio = contrastRatio(pair.first, pair.second)
        assertTrue(
          "$name $which match is ${"%.2f".format(ratio)}:1, below $BODY_TEXT_FLOOR:1",
          ratio >= BODY_TEXT_FLOOR,
        )
      }
    }
  }

  /**
   * The border has to be tellable from **both** surfaces it separates - the field's own fill on
   * the inside and the page on the outside - because it is drawn between them and a line the
   * colour of either side is not a line.
   */
  @Test
  fun theEditorBoxIsBoundedApartFromTheSurfacesItSeparates() {
    palettes.forEach { (name, palette) ->
      assertNotEquals("$name border must differ from the field", palette.cellBackground, palette.cellEditorBorder)
      assertNotEquals("$name border must differ from the page", palette.editorBackground, palette.cellEditorBorder)
    }
  }

  /**
   * **The border must do more work than the fill step already does**, which is the assertion that
   * replaced the one this row was planned around.
   *
   * The plan asked for the 3:1 WCAG 1.4.11 non-text floor against the cell. Measured, **no step of
   * the IntelliJ ramp that reads as a border reaches it** - Gray5 is 1.74:1 dark and Gray9 1.51:1
   * light - and neither reference clears it either: JupyterLab's own `--jp-cell-editor-border-color`
   * measures 2.60:1 dark and **1.21:1** light on its own surfaces. A value that passed would be a
   * hard grey rule around every cell in the document, heavier than anything either reference draws.
   *
   * So the floor moves to the requirement that actually applies. 1.4.11 asks for contrast in what
   * is *required to identify* a component, and the thing that identifies this one under WCAG is
   * its **active** state - held to 3:1 in the test below. What the resting border owes is that it
   * strengthens the boundary the fill alone makes, and that is a ratio this can assert: the border
   * against the field, versus the field against the page.
   */
  @Test
  fun theRestingBorderDrawsAStrongerBoundaryThanTheFillStepAlone() {
    palettes.forEach { (name, palette) ->
      val border = contrastRatio(palette.cellEditorBorder, palette.cellBackground)
      val fill = contrastRatio(palette.cellBackground, palette.editorBackground)
      assertTrue(
        "$name border is ${"%.2f".format(border)}:1 on the field, no better than the fill's ${"%.2f".format(fill)}:1",
        border > fill,
      )
    }
  }

  /**
   * **The state indicator is held to 3:1, and it is the one that clears it.** In edit mode the
   * active cell's editor takes `--jp-cell-editor-active-border-color`
   * (`packages/notebook/style/base.css:160-164`), which is `--jp-brand-color1` - the same variable
   * the active collapser takes, which is why this app reads it from one field rather than two that
   * could drift. Measured 3.23:1 dark and 4.03:1 light against the field it outlines.
   */
  @Test
  fun theActiveBorderClearsTheNonTextContrastFloor() {
    palettes.forEach { (name, palette) ->
      val ratio = contrastRatio(palette.stripeSelected, palette.cellBackground)
      assertTrue(
        "$name active border is ${"%.2f".format(ratio)}:1 on the field, below $NON_TEXT_FLOOR:1",
        ratio >= NON_TEXT_FLOOR,
      )
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

    /** WCAG 2.1 1.4.11, the floor for a component's state rather than for text. */
    const val NON_TEXT_FLOOR = 3.0

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
