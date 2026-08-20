package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's two editor palettes, dark and light.
 *
 * Every value is transcribed from a real IDE colour scheme rather than eyeballed from a
 * screenshot, so the syntax colours are ones that have been read by a lot of people for a long
 * time. The sources, for anyone checking a value:
 *
 * - `platform/platform-resources/src/themes/expUI/expUI_darkScheme.xml`
 * - `platform/platform-resources/src/themes/expUI/expUI_lightScheme.xml`
 * - `platform/platform-resources/src/themes/expUI/expUI_{dark,light}.theme.json` for the surfaces
 *
 * all under `reference/research_repos/`, which is gitignored - so these constants, and the
 * citations beside them, are the durable record.
 *
 * Two traps in reading those files, both hit while extracting these values:
 *
 * 1. **Hex is stored without leading zeros.** `value="33b3"` is `#0033B3` and `value="67d17"` is
 *    `#067D17`. Taken literally they parse as short, wrong colours.
 * 2. **Attribute keys inherit.** The markdown categories mostly have no colour of their own and
 *    resolve through `MarkdownHighlighterColors.java` to the general keys - which is why
 *    [SyntaxPalette] has no markdown fields.
 *
 * The notebook-specific `JUPYTER.*` keys are **not** in that tree (they ship with a closed
 * plugin), so the four cell surfaces are the only values not copied outright: they come from the
 * same grey ramp as the rest of the IDE chrome, which is where those defaults are derived from
 * anyway.
 */
val DarkEditorPalette: EditorPalette =
  EditorPalette(
    // Gray1 / the `TEXT` background.
    editorBackground = Color(0xFF1E1F22),
    // Gray2 - the New UI's panel surface, one step up from the editor page.
    cellBackground = Color(0xFF2B2D30),
    // Gray3.
    cellSelectionBackground = Color(0xFF393B40),
    // JupyterLab's own dark value: `rgba(33, 150, 243, 0.24)`
    // (`packages/theme-dark-extension/style/variables.css:298`). An alpha tint rather than a
    // solid, because it is drawn *over* the page and has to read as a wash on it.
    cellMultiSelected = Color(0x3D2196F3),
    // Blue6, the New UI accent.
    stripeSelected = Color(0xFF3574F0),
    // Gray5. One step above the `MATCHED_BRACE_ATTRIBUTES` grey, and the step is deliberate:
    // at Gray4 the resting stripe was invisible against the editor background on device
    // (evidence/u3-cell-chrome/01-resting-stripes.png, before this).
    stripeResting = Color(0xFF4E5157),
    // Red4.
    stripeError = Color(0xFF7A4343),
    caretRow = Color(0xFF26282E),
    lineNumber = Color(0xFF4B5059),
    outputForeground = Color(0xFFBCBEC4),
    // Gray7 in the light ramp is the same grey IntelliJ uses for de-emphasised labels; the dark
    // scheme's `DEFAULT_LINE_COMMENT` is the matching value here.
    executionTime = Color(0xFF7A7E85),
    matchedBracket = Color(0xFF43454A),
    // `SEARCH_RESULT_ATTRIBUTES` background, `expUI_darkScheme.xml:734`, with the foreground
    // JupyterLab's `.cm-searchMatch` would state - see [EditorPalette.searchMatchText].
    searchMatch = Color(0xFF2D543F),
    searchMatchText = Color(0xFFFFFFFF),
    // `--jp-search-selected-match-background-color`, `rgb(255, 225, 0)`
    // (`packages/theme-dark-extension/style/variables.css:416`).
    currentSearchMatch = Color(0xFFFFE100),
    currentSearchMatchText = Color(0xFF000000),
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

val LightEditorPalette: EditorPalette =
  EditorPalette(
    // Gray14 / the `TEXT` background.
    editorBackground = Color(0xFFFFFFFF),
    // Gray13, the New UI's panel surface.
    cellBackground = Color(0xFFF7F8FA),
    // Gray12.
    cellSelectionBackground = Color(0xFFEBECF0),
    // JupyterLab's own light value: `--md-blue-50`, `#e3f2fd`
    // (`packages/theme-light-extension/style/variables.css:295`).
    cellMultiSelected = Color(0xFFE3F2FD),
    // Blue4, the New UI accent.
    stripeSelected = Color(0xFF3574F0),
    // Gray8, matching the dark scheme's step up from the matched-brace grey.
    stripeResting = Color(0xFFA8ADBD),
    // Red4.
    stripeError = Color(0xFFDB3B4B),
    // Blue13.
    caretRow = Color(0xFFF5F8FE),
    lineNumber = Color(0xFFAEB3C2),
    outputForeground = Color(0xFF080808),
    // Gray6.
    executionTime = Color(0xFF6C707E),
    matchedBracket = Color(0xFF93D9D9),
    // `SEARCH_RESULT_ATTRIBUTES` background. The expUI light scheme does not override this key, so
    // it resolves to the default light scheme's own value
    // (`platform/platform-resources/src/DefaultColorSchemesManager.xml:688`).
    searchMatch = Color(0xFFCCCCFF),
    searchMatchText = Color(0xFF000000),
    // `--jp-search-selected-match-background-color`, `rgb(245, 200, 0)`
    // (`packages/theme-light-extension/style/variables.css:413`).
    currentSearchMatch = Color(0xFFF5C800),
    currentSearchMatchText = Color(0xFF000000),
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
