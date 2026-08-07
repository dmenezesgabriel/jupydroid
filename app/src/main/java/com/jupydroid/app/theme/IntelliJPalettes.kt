package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * The two palettes, read out of IntelliJ's own colour schemes rather than eyeballed from a
 * screenshot.
 *
 * Source, in the reference tree (`reference/research_repos/intellij-community/`):
 *
 * - `platform/platform-resources/src/themes/expUI/expUI_darkScheme.xml`
 * - `platform/platform-resources/src/themes/expUI/expUI_lightScheme.xml`
 * - `platform/platform-resources/src/themes/expUI/expUI_{dark,light}.theme.json` for the grey
 *   and blue ramps the surfaces come from
 *
 * These are the **New UI** schemes, not classic Darcula: they are what a current IntelliJ shows.
 * The names here keep "Darcula" because that is what users call the dark one.
 *
 * Two traps in reading those files, both hit while extracting these values:
 *
 * 1. **Hex is stored without leading zeros.** `value="33b3"` is `#0033B3` and `value="67d17"` is
 *    `#067D17`. Taken literally they parse as short, wrong colours.
 * 2. **Attribute keys inherit.** The markdown categories mostly have no colour of their own and
 *    resolve through `MarkdownHighlighterColors.java` to the general keys - which is why
 *    [SyntaxPalette] has no markdown fields.
 *
 * The Jupyter plugin's own `JUPYTER.*` keys are **not** in the community repository (the plugin
 * is closed), so the four cell surfaces are the only values here that are not copied outright:
 * they are taken from the New UI grey ramp that the rest of the IDE chrome uses, which is where
 * the plugin's own defaults come from.
 */
val DarculaEditorPalette: EditorPalette =
  EditorPalette(
    // Gray1 / the `TEXT` background.
    editorBackground = Color(0xFF1E1F22),
    // Gray2 - the New UI's panel surface, one step up from the editor page.
    cellBackground = Color(0xFF2B2D30),
    // Gray3.
    cellSelectionBackground = Color(0xFF393B40),
    // Blue6, the New UI accent.
    stripeSelected = Color(0xFF3574F0),
    // Gray4, the same grey `MATCHED_BRACE_ATTRIBUTES` uses for its background.
    stripeResting = Color(0xFF43454A),
    // Red4.
    stripeError = Color(0xFF7A4343),
    caretRow = Color(0xFF26282E),
    lineNumber = Color(0xFF4B5059),
    outputForeground = Color(0xFFBCBEC4),
    // Gray7 in the light ramp is the same grey IntelliJ uses for de-emphasised labels; the dark
    // scheme's `DEFAULT_LINE_COMMENT` is the matching value here.
    executionTime = Color(0xFF7A7E85),
    matchedBracket = Color(0xFF43454A),
    syntax =
      SyntaxPalette(
        plain = Color(0xFFBCBEC4),
        keyword = Color(0xFFCF8E6D),
        string = Color(0xFF6AAB73),
        number = Color(0xFF2AACB8),
        comment = Color(0xFF7A7E85),
        function = Color(0xFF56A8F5),
        heading = Color(0xFFC77DBB),
        // `MARKDOWN_LINK_TEXT` is overridden in the dark scheme, so it is used in preference to
        // the `HYPERLINK_ATTRIBUTES` it inherits from - and it is the more legible of the two
        // (5.47:1 against the cell background, where #548AF7 measures 4.18:1).
        link = Color(0xFF56A8F5),
      ),
  )

val IntelliJLightEditorPalette: EditorPalette =
  EditorPalette(
    // Gray14 / the `TEXT` background.
    editorBackground = Color(0xFFFFFFFF),
    // Gray13, the New UI's panel surface.
    cellBackground = Color(0xFFF7F8FA),
    // Gray12.
    cellSelectionBackground = Color(0xFFEBECF0),
    // Blue4, the New UI accent.
    stripeSelected = Color(0xFF3574F0),
    // Gray9.
    stripeResting = Color(0xFFC9CCD6),
    // Red4.
    stripeError = Color(0xFFDB3B4B),
    // Blue13.
    caretRow = Color(0xFFF5F8FE),
    lineNumber = Color(0xFFAEB3C2),
    outputForeground = Color(0xFF080808),
    // Gray6.
    executionTime = Color(0xFF6C707E),
    matchedBracket = Color(0xFF93D9D9),
    syntax =
      SyntaxPalette(
        plain = Color(0xFF080808),
        keyword = Color(0xFF0033B3),
        string = Color(0xFF067D17),
        number = Color(0xFF1750EB),
        comment = Color(0xFF8C8C8C),
        function = Color(0xFF00627A),
        heading = Color(0xFF871094),
        // The light scheme has no markdown override, so this is `HYPERLINK_ATTRIBUTES`.
        link = Color(0xFF0006DC),
      ),
  )
