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
    // `notebook.editorBackground`, which 2026 Dark leaves unset -> `editorPane.background` ->
    // `editor.background`. Two hops, and `res/values-night/colors.xml` holds the same value for
    // the window drawn before Compose exists.
    editorBackground = Color(0xFF121314),
    // `notebook.cellEditorBackground` -> `sideBar.background`. A cell is one step *up* from the
    // page in dark, which is the direction the IntelliJ ramp had too.
    cellBackground = Color(0xFF191A1B),
    // `textPreformat.background` / `.foreground`, which is what the notebook's own markdown
    // webview takes for code (`webviewThemeMapping.ts:20-21`).
    inlineCodeBackground = Color(0xFF262626),
    inlineCodeForeground = Color(0xFF8C8C8C),
    // `notebook.selectedCellBackground`, which 2026 Dark leaves unset ->
    // `list.inactiveSelectionBackground`. Opaque here where light's is a wash, and that is the
    // reference's own asymmetry rather than a transcription slip.
    cellMultiSelected = Color(0xFF2C2D2E),
    // `notebook.cellBorderColor`, unset in 2026 Dark -> `transparent(listInactiveSelectionBackground, 1)`,
    // which is `list.inactiveSelectionBackground` at full opacity - the same value `cellMultiSelected`
    // resolves to. Two ids, one colour, and that is the reference's own doing.
    cellEditorBorder = Color(0xFF2C2D2E),
    // `notebook.focusedCellBorder` and `notebook.focusedEditorBorder`, both unset -> `focusBorder`.
    // One field for both because the reference resolves both to one id, which is the same reason
    // this field held one value under JupyterLab. **Translucent**: 70% over whatever it outlines.
    stripeSelected = Color(0xB33994BC),
    // `notebookStatusErrorIcon.foreground` -> `errorForeground`.
    stripeError = Color(0xFFF48771),
    // `editorLineNumber.foreground`.
    lineNumber = Color(0xFF858889),
    // `descriptionForeground`. The cell status bar states no colour of its own in the reference
    // (`media/notebook.css` gives `.cell-statusbar-container` no `color` rule), so it inherits the
    // secondary-text role, which is the id `ColorRamp` already calls `onSurfaceVariant`.
    executionTime = Color(0xFF8C8C8C),
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
    // `notebook.editorBackground` -> `editorPane.background` -> `editor.background`. The one
    // surface the swap does not move: IntelliJ's Gray14 was already `#FFFFFF`.
    editorBackground = Color(0xFFFFFFFF),
    // `notebook.cellEditorBackground` -> `sideBar.background`. A cell is one step *down* from the
    // page in light, the mirror of dark.
    cellBackground = Color(0xFFFAFAFD),
    // `textPreformat.background` / `.foreground` - see the dark palette's note.
    inlineCodeBackground = Color(0xFFECECEC),
    inlineCodeForeground = Color(0xFF606060),
    // `notebook.selectedCellBackground`, which 2026 Light **does** set, and sets translucent:
    // `#C8DDF150`. VS Code writes `#RRGGBBAA` and Compose writes `0xAARRGGBB`, so the alpha
    // swaps ends - transcribed in the reference's own order this would be a near-black at 79%.
    cellMultiSelected = Color(0x50C8DDF1),
    // `notebook.cellBorderColor`, which 2026 Light **does** set.
    cellEditorBorder = Color(0xFFE5E5E5),
    // `focusBorder`, and opaque here where dark's carries 70% - see the dark palette's note.
    stripeSelected = Color(0xFF0069CC),
    // `notebookStatusErrorIcon.foreground` -> `errorForeground`.
    stripeError = Color(0xFFAD0707),
    // `editorLineNumber.foreground`.
    lineNumber = Color(0xFF606060),
    // `descriptionForeground` - see the dark palette's note.
    executionTime = Color(0xFF606060),
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
