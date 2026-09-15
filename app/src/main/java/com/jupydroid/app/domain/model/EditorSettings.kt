package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide editor preferences P6c grows - eight fields today.
 *
 * `notebook:toggle-autoclosing-brackets` writes the settings registry there
 * (`packages/notebook-extension/src/index.ts:2223-2246`), and its universal sibling
 * `fileeditor:toggle-autoclosing-brackets-universal` flips the notebook, the console and the file
 * editor together (`packages/fileeditor-extension/src/commands.ts:552-575`) - which is already
 * this app's shape, since `PythonTypingRules` is used by both a cell and the plain file editor.
 * One app-wide setting *is* the universal command here, not a simplification of the per-surface
 * ones.
 *
 * **On by default**, departing from the reference's own `default: false`
 * (`packages/codemirror/src/extension.ts:636-644`) - on a soft keyboard `(` and `)` are two trips
 * through the symbol row. See `docs/DECISIONS.md`.
 */
/**
 * The two numbering fields are `toc:display-numbering` and `toc:display-h1-numbering`, at the
 * schema's own defaults - `numberHeaders` **false**, `numberingH1` **true**
 * (`packages/toc-extension/schema/registry.json:32-69`). They live here rather than beside the
 * open notebook because the reference keeps them in its settings registry too, applied to every
 * ToC model at once (`packages/toc-extension/src/index.ts:219-258`), and because the phone's
 * surface for the ToC has no toolbar of its own to put them on.
 *
 * New fields must carry a default: an `editor-settings.json` written before they existed is read
 * with `ignoreUnknownKeys` and has nothing to say about them.
 */
@Serializable
data class EditorSettings(
  val autoClosingBrackets: Boolean = true,
  val tocNumberHeaders: Boolean = false,
  val tocNumberingH1: Boolean = true,
  /**
   * `toc:display-outputs-numbering` - whether the headings a code cell *printed* are listed
   * alongside the ones its neighbours wrote. **True**, which is the schema's own default
   * (`packages/toc-extension/schema/registry.json:32-69`).
   */
  val tocIncludeOutput: Boolean = true,
  /**
   * `accessKernelHistory` - whether a cell being edited can step through what the kernel has run.
   *
   * **False, and this is the first field here that keeps the reference's default rather than
   * departing from it** (`packages/notebook-extension/schema/tracker.json:873-878`,
   * `packages/notebook/src/widget.ts:1488`). It is opt-in there by instruction as well as by
   * value - *"To use this feature, enable kernel history access in the notebook settings"*
   * (`docs/source/user/notebook.md:191`) - and both `accessPreviousHistory` and
   * `accessNextHistory` return on their first line without it
   * (`packages/notebook/src/actions.tsx:2616`, `:2634`).
   *
   * The two departures beside it were bought with a reason a phone supplies: brackets that close
   * themselves save a trip through the symbol row, and a gutter names the line a traceback blames
   * on a screen that shows a fraction of a cell. Nothing about a touch screen makes a cell's text
   * safer to overwrite, so this one is left where the reference put it.
   */
  val accessKernelHistory: Boolean = false,
  /**
   * `notebook:toggle-all-cell-line-numbers` - P6c-2 grew this repository with it rather than
   * leaving it in `EditorUiState`, where it had been per notebook and per sitting since P2g: five
   * other fields here were already app-wide and persisted, and a sixth toggle living somewhere
   * else for no reason but history was the exact drift `docs/PLAN.md`'s P6c row existed to close.
   *
   * **True by default**, matching what `EditorUiState.lineNumbersShown` always defaulted to.
   */
  val lineNumbersShown: Boolean = true,
  /**
   * `docs/PLAN.md`'s P6c-4 - a cell's own code font size, distinct from
   * [com.jupydroid.app.domain.model.TerminalFontSize] because a cell is an editor concern where
   * the terminal's own glyph size explicitly is not (see that object's own doc comment). Bounds
   * live beside it in [CodeFontSize], clamped by the screen that writes here rather than by this
   * repository - the same division [TerminalSettingsRepository][com.jupydroid.app.domain.port.TerminalSettingsRepository]
   * draws for the terminal's own field.
   *
   * **Defaults to [CodeFontSize.DEFAULT]**, `CodeText.size`'s own value, so shipping this field
   * changes no pixel until a user acts - `TerminalFontSize.DEFAULT`'s own reasoning.
   */
  val codeFontSizeSp: Int = CodeFontSize.DEFAULT,
  /**
   * `docs/PLAN.md`'s P6c-5 - whether a code cell's source wraps or scrolls horizontally.
   * Markdown and raw cells carry no such field: the reference hardwires them to wrap
   * (`packages/notebook/src/widget.ts:1335-1352`, `defaultEditorConfig.markdown/raw.lineWrap:
   * true`, no toggle), so this one governs code only - [com.jupydroid.app.ui.editor.CodeCellField]
   * and the plain file editor, which reuses it.
   *
   * **True by default**, departing from the reference's own `codeCellConfig.lineWrap` default of
   * `false` (`packages/notebook-extension/schema/tracker.json:699-706`) - the same shape
   * [autoClosingBrackets]'s own departure is: horizontal drag-to-scroll of source code is worse
   * touch ergonomics than desktop's mouse wheel, so shipping this field changes no pixel until a
   * user turns it off.
   */
  val codeWrapEnabled: Boolean = true,
)

/**
 * `docs/PLAN.md` P6c-4's own bounds - measured on device against a real code cell rather than
 * copied from [TerminalFontSize]'s 4-256sp/±2, which were themselves measured against a
 * different surface (Termux's terminal, not a notebook cell). `STEP` of 1 matches the
 * reference's own increment for this exact setting (`apputils:incr-font-size`/`decr-font-size`
 * on the `code-font-size` key step by 1px every time, `ThemeManager._incrFontSize`,
 * `packages/apputils/src/thememanager.ts:380-389` - unbounded there, since CSS imposes no limit
 * a touch screen's own layout does).
 */
object CodeFontSize {
  const val MIN = 8
  const val MAX = 32
  const val STEP = 1
  const val DEFAULT = 14
}
