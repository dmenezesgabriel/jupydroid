package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's two editor palettes, dark and light.
 *
 * **Two references meet in this file, and which one answers is per value.** The syntax colours
 * are VS Code's, transcribed from the **resolved** `2026-dark.json` / `2026-light.json` read out
 * of `reference/research_repos/vscode` at `f3fa55c39d3`. Everything above `syntax` - the
 * surfaces, the stripes, the search and bracket highlights - is still IntelliJ's until P2u-3
 * re-sources it onto VS Code's `notebook.*` colour ids. `theme/Theme.kt` states the same split
 * from the chrome's side.
 *
 * **Resolved is the whole trap.** `2026-dark.json` is the outermost file of a four-deep `include`
 * chain and overrides most of what it inherits; reading any single file gives the wrong colour
 * (`docs/DECISIONS.md`, "The default VS Code theme is 2026 Dark / 2026 Light, not Dark Modern").
 * Every syntax value below is reproducible per role:
 *
 * ```sh
 * python3 scripts/resolve_vscode_theme.py \
 *   reference/research_repos/vscode/extensions/theme-defaults/themes/2026-dark.json --role keyword
 * ```
 *
 * **Parity is the rule, and it outranks the contrast floor.** Every syntax value below is the
 * 2026 theme's, verbatim, including the one that measures under WCAG AA: light's `number` at
 * **4.42:1** on the cell surface. `EditorPaletteTest` therefore pins these values *by equality*
 * rather than by ratio - a colour that drifts off the reference fails, and a colour the reference
 * itself draws faintly does not. `docs/DECISIONS.md`, "Parity with the 2026 themes outranks the
 * contrast floor", carries what that trades away and what still holds.
 *
 * The IntelliJ schemes these syntax colours replaced, for anyone reading back through `git log`,
 * were `platform/platform-resources/src/themes/expUI/expUI_{dark,light}Scheme.xml`.
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
    // Gray5 - IntelliJ's `Component.borderColor`. The same ramp step P2m-1 removed from the
    // gutter, which is the point: the reference draws cell bounds on the box and not beside it.
    cellEditorBorder = Color(0xFF4E5157),
    // Blue6, the New UI accent.
    stripeSelected = Color(0xFF3574F0),
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
      plain = Color(0xFFBBBEBF),
      keyword = Color(0xFFC586C0),
      string = Color(0xFFA5D6FF),
      number = Color(0xFFB5CEA8),
      comment = Color(0xFF8B949E),
      function = Color(0xFFD2A8FF),
      heading = Color(0xFF79C0FF),
      // `string.other.link`, which is `string`'s value here. The underline is what tells them
      // apart, and that is the reference's structure - see [SyntaxPalette.link].
      link = Color(0xFFA5D6FF),
      codeSpan = Color(0xFF79C0FF),
      blockQuote = Color(0xFF7EE787),
      listMarker = Color(0xFFFFA657),
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
    // Gray9 - IntelliJ's `Component.borderColor`.
    cellEditorBorder = Color(0xFFC9CCD6),
    // Blue4, the New UI accent.
    stripeSelected = Color(0xFF3574F0),
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
      plain = Color(0xFF202020),
      keyword = Color(0xFFAF00DB),
      string = Color(0xFF0A3069),
      // **Under AA, and kept anyway - the one value in this file that is.** On the cell surface
      // `notebook.cellEditorBackground` resolves to this measures **4.42:1**, against a 4.5:1
      // floor. It is what VS Code itself renders: 2026 Light never restates `constant.numeric`,
      // so it keeps `light_vs.json`'s value from three files down. Parity is the rule here and
      // the shortfall is recorded rather than corrected - `docs/DECISIONS.md`, "Parity with the
      // 2026 themes outranks the contrast floor".
      number = Color(0xFF098658),
      comment = Color(0xFF6E7781),
      function = Color(0xFF8250DF),
      heading = Color(0xFF0550AE),
      // `string.other.link`, which is `string`'s value here - see [SyntaxPalette.link].
      link = Color(0xFF0A3069),
      // `markup.inline.raw`, which is `markup.heading`'s value in this theme. Both are the
      // reference's own, and nothing here is owed a distinction the reference does not draw.
      codeSpan = Color(0xFF0550AE),
      blockQuote = Color(0xFF116329),
      listMarker = Color(0xFF953800),
    ),
  )
