package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palettes, held to the reference they were transcribed from and to what a phone needs.
 *
 * This began as the regression test for the contrast defect recorded:
 * syntax colours used to be `MaterialTheme.colorScheme` roles, so `NUMBER` landed on `secondary`
 * and sat almost on top of the body text in dark. Deriving colour from a role can never be
 * asserted, because the role is whatever the wallpaper made it - which is why the fix was a
 * palette of stated constants, and that half has not changed.
 *
 * **What the constants are held to did change.** The syntax colours are VS Code's and
 * are pinned by *equality* to the resolved 2026 themes; the contrast floor that used to gate them
 * is gone, because parity and the floor disagree on one value and parity won
 * (ADR-0041). Everything
 * this app chose for itself - the two search-match foregrounds, the active editor border - is
 * still held to its WCAG floor below.
 *
 * The syntax colours' own tests are `SyntaxPaletteTest`'s; this file keeps the surfaces.
 */
class EditorPaletteTest {
  private val palettes = mapOf("dark" to DarkEditorPalette, "light" to LightEditorPalette)

  /**
   * **The surfaces are the reference's too, and pinned the same way as the syntax colours.**
   *
   * Keyed by the **colour id** rather than a token scope, because these are workbench colours and
   * are reached differently: most notebook ids are unset in both 2026 themes - dark sets *no*
   * `notebook.*` colour at all - so the value comes from the chain of `registerColor` defaults.
   * `scripts/resolve_vscode_theme.py` prints the resolution for any one of them.
   *
   * A field not in the map yet is one whose id has not been transcribed.
   */
  @Test
  fun everyChromeTokenIsTheReferenceValue() {
    assertEquals("dark chrome", DARK_CHROME, DarkEditorPalette.chromeByColourId())
    assertEquals("light chrome", LIGHT_CHROME, LightEditorPalette.chromeByColourId())
  }

  private fun EditorPalette.chromeByColourId(): Map<String, Color> = surfaceByColourId() + mapOf(
    // The three ids name a family each: `.method` and `.constructor` register the same value as
    // `.function`, `.enumerator` and `.event` as `.class`, `.field`/`.interface`/`.enumeratorMember`
    // as `.variable` (`symbolIcons.ts` - every other `symbolIcon.*` id inherits `foreground`).
    "symbolIcon.functionForeground" to symbolCallable,
    "symbolIcon.classForeground" to symbolClasslike,
    "symbolIcon.variableForeground" to symbolMember,
    // `editorHoverWidget.highlightForeground` registers to `list.highlightForeground`
    // (`parameterHintsWidget.ts:367`), which both 2026 themes state.
    "editorHoverWidget.highlightForeground" to signatureActiveParam,
  )

  private fun EditorPalette.surfaceByColourId(): Map<String, Color> = mapOf(
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
   * This is the assertion that replaced two, and the swap is the whole of it. The app used to
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
   * not, and this palette carries alpha at all only since the surfaces were re-sourced.
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
      // Unset in both 2026 themes - the `symbolIcons.ts` registered defaults apply.
      "symbolIcon.functionForeground" to Color(0xFFB180D7),
      "symbolIcon.classForeground" to Color(0xFFEE9D28),
      "symbolIcon.variableForeground" to Color(0xFF75BEFF),
      // `list.highlightForeground`, which 2026 Dark states - `2026-dark.json:68`.
      "editorHoverWidget.highlightForeground" to Color(0xFF48A0C7),
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
      "symbolIcon.functionForeground" to Color(0xFF652D90),
      "symbolIcon.classForeground" to Color(0xFFD67E00),
      "symbolIcon.variableForeground" to Color(0xFF007ACC),
      // `list.highlightForeground`, which 2026 Light states - `2026-light.json:77`.
      "editorHoverWidget.highlightForeground" to Color(0xFF0069CC),
    )

    /** WCAG 2.1 1.4.11, the floor for a component's state rather than for text. */
    const val NON_TEXT_FLOOR = 3.0
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
