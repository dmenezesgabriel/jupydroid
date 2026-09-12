package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.jupydroid.app.syntax.SyntaxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palettes, held to the reference they were transcribed from and to what a phone needs.
 *
 * This began as the regression test for the contrast defect recorded in `docs/DECISIONS.md`:
 * syntax colours used to be `MaterialTheme.colorScheme` roles, so `NUMBER` landed on `secondary`
 * and sat almost on top of the body text in dark. Deriving colour from a role can never be
 * asserted, because the role is whatever the wallpaper made it - which is why the fix was a
 * palette of stated constants, and that half has not changed.
 *
 * **What the constants are held to did change, at P2u-2.** The syntax colours are VS Code's and
 * are pinned by *equality* to the resolved 2026 themes; the contrast floor that used to gate them
 * is gone, because parity and the floor disagree on one value and parity won
 * (`docs/DECISIONS.md`, "Parity with the 2026 themes outranks the contrast floor"). Everything
 * this app chose for itself - the two search-match foregrounds, the active editor border - is
 * still held to its WCAG floor below, and P2u-3 brings the surfaces onto the same footing.
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
   * **Every syntax colour is the 2026 theme's own value, and equality is the gate.**
   *
   * This replaced a WCAG AA floor over the same values, and the swap is a decision rather than a
   * regression: with VS Code as the presentation reference, *parity* is what the palette owes, and
   * the floor and parity disagree in exactly one place - light's `number` `#098658`, which the
   * reference itself draws at **4.42:1** on the cell surface. `docs/DECISIONS.md`, "Parity with
   * the 2026 themes outranks the contrast floor", carries what that trades away.
   *
   * Equality is the stronger assertion of the two for everything else. A ratio lets a value be
   * swapped for any other legible colour; this lets it be swapped for nothing at all, which is
   * what *a value may not be nudged "to match VS Code more closely" without re-measuring* asks
   * for. The keys are the scopes, so a failure names the command that reproduces it:
   *
   * ```sh
   * python3 scripts/resolve_vscode_theme.py \
   *   reference/research_repos/vscode/extensions/theme-defaults/themes/2026-dark.json --role number
   * ```
   *
   * The app's *own* foregrounds are still held to AA - see the two search-match tests below. Those
   * are values this app chose, not values it transcribed, and nothing about parity excuses them.
   */
  @Test
  fun everySyntaxColourIsTheReferenceValue() {
    assertEquals("dark is not 2026 Dark", DARK_2026, DarkEditorPalette.syntax.byScope())
    assertEquals("light is not 2026 Light", LIGHT_2026, LightEditorPalette.syntax.byScope())
  }

  /**
   * **The surfaces are the reference's too, and pinned the same way as the syntax colours.**
   *
   * Keyed by the **colour id** rather than a token scope, because these are workbench colours and
   * are reached differently: most notebook ids are unset in both 2026 themes - dark sets *no*
   * `notebook.*` colour at all - so the value comes from the chain of `registerColor` defaults.
   * `docs/PLAN.md`, "What P2u-3 is transcribing", carries each resolution.
   *
   * The map grows one sub-step at a time. A field not in it yet is one P2u-3 has not reached.
   */
  @Test
  fun everyChromeTokenIsTheReferenceValue() {
    assertEquals("dark chrome", DARK_CHROME, DarkEditorPalette.chromeByColourId())
    assertEquals("light chrome", LIGHT_CHROME, LightEditorPalette.chromeByColourId())
  }

  private fun EditorPalette.chromeByColourId(): Map<String, Color> = mapOf(
    "notebook.editorBackground" to editorBackground,
    "notebook.cellEditorBackground" to cellBackground,
    "notebook.selectedCellBackground" to cellMultiSelected,
    "notebook.cellBorderColor" to cellEditorBorder,
    "focusBorder" to stripeSelected,
    "notebookStatusErrorIcon.foreground" to stripeError,
    "editorLineNumber.foreground" to lineNumber,
    "debugIcon.breakpointForeground" to breakpointVerified,
    "debugIcon.breakpointUnverifiedForeground" to breakpointUnverified,
    "editor.stackFrameHighlightBackground" to debugStoppedLine,
    "descriptionForeground" to executionTime,
    "textPreformat.background" to preformatBackground,
    "textPreformat.foreground" to preformatForeground,
    "textBlockQuote.background" to blockQuoteBackground,
    "textBlockQuote.border" to blockQuoteBorder,
    "editorBracketMatch.background" to matchedBracket,
    "editor.findMatchHighlightBackground" to searchMatch,
    "editor.findMatchBackground" to currentSearchMatch,
  )

  /** Each value against the VS Code scope it was read from, so a failure says which rule moved. */
  private fun SyntaxPalette.byScope(): Map<String, Color> = mapOf(
    "editor.foreground" to plain,
    "keyword.control" to keyword,
    "string" to string,
    "constant.numeric" to number,
    "comment" to comment,
    "entity.name.function" to function,
    "markup.heading" to heading,
    "string.other.link" to link,
    "markup.inline.raw" to codeSpan,
    "markup.quote" to blockQuote,
    "punctuation.definition.list.begin.markdown" to listMarker,
  )

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
   * **A match is a wash over the code, not a fill under a restated foreground.**
   *
   * This is the assertion that replaced two, and the swap is the whole of P2u-3e. The app used to
   * paint an opaque background and state the text colour on top, because `EditorPalette`'s two
   * `*Text` fields existed - JupyterLab's answer, which the app took after measuring that
   * IntelliJ's left `DEFAULT_KEYWORD` at 3.16:1 inside a match.
   *
   * VS Code answers the same question the other way and does not state a foreground at all:
   * `editor.findMatchForeground` and `editor.findMatchHighlightForeground` are both registered
   * `null` (`platform/theme/common/colors/editorColors.ts:152,160`), and every match background is
   * translucent so the syntax colour underneath survives - the registration for
   * `findMatchHighlight` says so outright, *"must not be opaque so as not to hide underlying
   * decorations"*. Translucency is therefore load-bearing: opaque values here would hide the code
   * and there is no foreground left to rescue it.
   */
  @Test
  fun bothKindsOfMatchAreAWashRatherThanAFill() {
    palettes.forEach { (name, palette) ->
      mapOf("found" to palette.searchMatch, "current" to palette.currentSearchMatch)
        .forEach { (which, wash) ->
          assertTrue(
            "$name $which match is opaque at alpha ${wash.alpha}; it would hide the code under it",
            wash.alpha < 1f,
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
   * **The state indicator is held to 3:1, measured on what is actually painted.**
   *
   * In edit mode the active cell's editor takes `notebook.focusedEditorBorder` and the active
   * cell's frame `notebook.focusedCellBorder`; both are unset in the 2026 themes and both resolve
   * to `focusBorder`, which is why this app reads them from one field rather than two that could
   * drift.
   *
   * **The ratio is taken after compositing, and that is not a refinement.** Dark's `focusBorder`
   * is `#3994BCB3` - 70% opaque - so the hex in the palette is a colour no pixel ever is. Measured
   * raw it reads 5.09:1; painted over the cell it is `#2F708C` and **3.17:1**. Both clear the
   * floor, so nothing here changed because of the number - it changed because a test that measures
   * an uncomposited value would keep passing after the alpha was lowered to something that does
   * not, and P2u-3 is the step that put alpha in this palette at all.
   */
  @Test
  fun theActiveBorderClearsTheNonTextContrastFloor() {
    palettes.forEach { (name, palette) ->
      val painted = palette.stripeSelected.compositeOver(palette.cellBackground)
      val ratio = contrastRatio(painted, palette.cellBackground)
      assertTrue(
        "$name active border paints as $painted, ${"%.2f".format(ratio)}:1, below $NON_TEXT_FLOOR:1",
        ratio >= NON_TEXT_FLOOR,
      )
    }
  }

  /**
   * **Inline code, a block quote and a list marker each have a colour of their own.**
   *
   * They used to fold onto `string` and `keyword`, and the fold was IntelliJ's rather than an
   * economy of ours - `MarkdownHighlighterColors.java:24-41` maps `MARKDOWN_CODE_SPAN` and
   * `MARKDOWN_BLOCK_QUOTE` both onto `DEFAULT_STRING` and list markers onto `DEFAULT_KEYWORD`.
   * The VS Code re-base retired that citation and the reference it moved to has a rule for each:
   * `markup.inline.raw`, `markup.quote` and `punctuation.definition.list.begin.markdown`.
   *
   * Asserted as *not the folded value* rather than as a hex, because the hexes are already pinned
   * by [everySyntaxColourIsTheReferenceValue] - what this catches is the fold coming back.
   */
  @Test
  fun inlineCodeAndQuoteAndMarkerAreNotFoldedOntoStringAndKeyword() {
    palettes.forEach { (name, palette) ->
      val styles = palette.syntax.styles()
      val folds = mapOf(
        SyntaxKind.CODE to ("string" to palette.syntax.string),
        SyntaxKind.QUOTE to ("string" to palette.syntax.string),
        SyntaxKind.MARKER to ("keyword" to palette.syntax.keyword),
      )
      val refolded = folds.filter { (kind, onto) -> styles.getValue(kind).color == onto.second }
        .map { (kind, onto) -> "$kind folded onto ${onto.first}" }
      assertEquals("$name kinds that fell back onto the general palette", emptyList<String>(), refolded)
    }
  }

  /**
   * The three are distinct from each other too, since a markdown cell shows all three at once -
   * which the `u1-palette` fixture is built to put in one frame.
   */
  @Test
  fun theMarkdownOnlyKindsAreMutuallyDistinct() {
    palettes.forEach { (name, palette) ->
      val colours = MARKDOWN_KINDS.map { palette.syntax.styles().getValue(it).color }
      assertEquals("$name has duplicate markdown syntax colours", colours.size, colours.toSet().size)
    }
  }

  /**
   * **A link takes the string colour, and the underline is the whole of what tells them apart.**
   *
   * That is the reference's structure rather than an economy of ours. VS Code paints a link's
   * title with `string.other.link`, which resolves to the same value as `string` in both 2026
   * themes - `#a5d6ff` dark, `#0a3069` light - and puts the signal in `markup.underline`, whose
   * rule sets `fontStyle` and no colour at all.
   *
   * Asserted as a pair because either half alone is a defect: the shared colour with the
   * decoration dropped makes a link indistinguishable from a string, and giving the link a colour
   * of its own would depart from the reference to solve a problem the reference does not have.
   */
  @Test
  fun aLinkTakesTheStringColourAndIsToldApartByItsUnderline() {
    palettes.forEach { (name, palette) ->
      val link = palette.syntax.styles().getValue(SyntaxKind.LINK)
      assertEquals("$name link must take the string colour", palette.syntax.string, link.color)
      assertEquals("$name link must be underlined", TextDecoration.Underline, link.textDecoration)
    }
  }

  @Test
  fun theTwoPalettesAreNotTheSamePalette() {
    assertNotEquals(DarkEditorPalette.editorBackground, LightEditorPalette.editorBackground)
    assertNotEquals(DarkEditorPalette.syntax.keyword, LightEditorPalette.syntax.keyword)
  }

  private companion object {
    const val BODY_TEXT_FLOOR = 4.5

    /**
     * `notebook.editorBackground` -> `editorPane.background` -> `editor.background`;
     * `notebook.cellEditorBackground` -> `sideBar.background`;
     * `notebook.selectedCellBackground` -> `list.inactiveSelectionBackground`, dark setting none.
     */
    val DARK_CHROME = mapOf(
      "notebook.editorBackground" to Color(0xFF121314),
      "notebook.cellEditorBackground" to Color(0xFF191A1B),
      "notebook.selectedCellBackground" to Color(0xFF2C2D2E),
      "notebook.cellBorderColor" to Color(0xFF2C2D2E),
      "focusBorder" to Color(0xB33994BC),
      "notebookStatusErrorIcon.foreground" to Color(0xFFF48771),
      "editorLineNumber.foreground" to Color(0xFF858889),
      "debugIcon.breakpointForeground" to Color(0xFFE51400),
      "debugIcon.breakpointUnverifiedForeground" to Color(0xFF848484),
      "editor.stackFrameHighlightBackground" to Color(0x33FFFF00),
      "descriptionForeground" to Color(0xFF8C8C8C),
      "textPreformat.background" to Color(0xFF262626),
      "textPreformat.foreground" to Color(0xFF8C8C8C),
      "textBlockQuote.background" to Color(0xFF242526),
      "textBlockQuote.border" to Color(0xFF2A2B2C),
      "editorBracketMatch.background" to Color(0x553994BC),
      "editor.findMatchHighlightBackground" to Color(0x80276782),
      "editor.findMatchBackground" to Color(0x90276782),
    )

    /**
     * Light states `notebook.selectedCellBackground` itself, and states it **translucent**.
     *
     * `#C8DDF150` is VS Code's `#RRGGBBAA`; Compose writes `0xAARRGGBB`, so the alpha that reads
     * last there reads first here. Transcribing it in the reference's own order would give a
     * near-black at 79% opacity instead of a pale blue at 31%.
     */
    val LIGHT_CHROME = mapOf(
      "notebook.editorBackground" to Color(0xFFFFFFFF),
      "notebook.cellEditorBackground" to Color(0xFFFAFAFD),
      "notebook.selectedCellBackground" to Color(0x50C8DDF1),
      "notebook.cellBorderColor" to Color(0xFFE5E5E5),
      "focusBorder" to Color(0xFF0069CC),
      "notebookStatusErrorIcon.foreground" to Color(0xFFAD0707),
      "editorLineNumber.foreground" to Color(0xFF606060),
      "debugIcon.breakpointForeground" to Color(0xFFE51400),
      "debugIcon.breakpointUnverifiedForeground" to Color(0xFF848484),
      "editor.stackFrameHighlightBackground" to Color(0x73FFFF66),
      "descriptionForeground" to Color(0xFF606060),
      "textPreformat.background" to Color(0xFFECECEC),
      "textPreformat.foreground" to Color(0xFF606060),
      "textBlockQuote.background" to Color(0xFFEAEAEA),
      "textBlockQuote.border" to Color(0xFFF0F1F2),
      "editorBracketMatch.background" to Color(0x400069CC),
      "editor.findMatchHighlightBackground" to Color(0x1A0069CC),
      "editor.findMatchBackground" to Color(0x400069CC),
    )

    /** `2026-dark.json`, resolved through its four-deep `include` chain. */
    val DARK_2026 = mapOf(
      "editor.foreground" to Color(0xFFBBBEBF),
      "keyword.control" to Color(0xFFC586C0),
      "string" to Color(0xFFA5D6FF),
      "constant.numeric" to Color(0xFFB5CEA8),
      "comment" to Color(0xFF8B949E),
      "entity.name.function" to Color(0xFFD2A8FF),
      "markup.heading" to Color(0xFF79C0FF),
      "string.other.link" to Color(0xFFA5D6FF),
      "markup.inline.raw" to Color(0xFF79C0FF),
      "markup.quote" to Color(0xFF7EE787),
      "punctuation.definition.list.begin.markdown" to Color(0xFFFFA657),
    )

    /** `2026-light.json`, resolved. `constant.numeric` is the value that measures 4.42:1. */
    val LIGHT_2026 = mapOf(
      "editor.foreground" to Color(0xFF202020),
      "keyword.control" to Color(0xFFAF00DB),
      "string" to Color(0xFF0A3069),
      "constant.numeric" to Color(0xFF098658),
      "comment" to Color(0xFF6E7781),
      "entity.name.function" to Color(0xFF8250DF),
      "markup.heading" to Color(0xFF0550AE),
      "string.other.link" to Color(0xFF0A3069),
      "markup.inline.raw" to Color(0xFF0550AE),
      "markup.quote" to Color(0xFF116329),
      "punctuation.definition.list.begin.markdown" to Color(0xFF953800),
    )

    /** WCAG 2.1 1.4.11, the floor for a component's state rather than for text. */
    const val NON_TEXT_FLOOR = 3.0

    /** The kinds only a markdown cell emits and that carry a colour of their own. */
    val MARKDOWN_KINDS = listOf(SyntaxKind.CODE, SyntaxKind.QUOTE, SyntaxKind.MARKER)

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

/**
 * [this] painted over [background] at its own alpha - the colour a pixel actually takes.
 *
 * Test-only, beside [contrastRatio] and for the same reason: the app does not composite at
 * runtime, Compose does. What has to be true is a fact about the constants.
 */
internal fun Color.compositeOver(background: Color): Color {
  val over = { channel: Float, under: Float -> channel * alpha + under * (1 - alpha) }
  return Color(over(red, background.red), over(green, background.green), over(blue, background.blue))
}

private fun relativeLuminance(color: Color): Double =
  0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

private fun linear(channel: Float): Double {
  val c = channel.toDouble()
  return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}
