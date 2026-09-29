package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide editor preferences - fourteen fields today.
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
 * through the symbol row.
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
   * `notebook:toggle-all-cell-line-numbers`. It lives here rather than in `EditorUiState`, where
   * it had been per notebook and per sitting: five other fields here were already app-wide and
   * persisted, and a sixth toggle living somewhere else for no reason but history is drift.
   *
   * **True by default**, matching what `EditorUiState.lineNumbersShown` always defaulted to.
   */
  val lineNumbersShown: Boolean = true,
  /**
   * A cell's own code font size, distinct from
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
   * Whether a code cell's source wraps or scrolls horizontally.
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
  /**
   * Whether a code cell's editor asks the kernel for completions at all.
   *
   * The reference has two unrelated settings here and only one is an on/off: `availableProviders`
   * (`packages/completer-extension/schema/manager.json:1-50`) ranks each completion provider, and a
   * negative rank disables it - `"CompletionProvider:kernel": 550` by default, filtered out once
   * negative (`packages/completer-extension/src/index.ts:479-484`,
   * `packages/completer/src/manager.ts:145-156`). This app has exactly one provider, the kernel, so
   * a boolean is a faithful collapse of that ranked list rather than a simplification of it - the
   * same collapse [com.jupydroid.app.domain.model.ThemeOverride] already is for the reference's
   * open-ended theme registry. **Not** the reference's `autoCompletion` (default `false`,
   * `packages/completer-extension/schema/manager.json:1-50`): that setting never gates the kernel
   * request itself, only whether a single typed character auto-opens the popup versus requiring
   * explicit Tab (`packages/completer/src/handler.ts:349-360`) - a trigger-mode concern this app has
   * no Tab-invoke command to make sense of yet, and out of scope here.
   *
   * **True by default**, so shipping this field changes no pixel until a user turns it off - the
   * same reasoning [codeWrapEnabled]'s own default carries.
   */
  val completionEnabled: Boolean = true,
  /**
   * Whether [com.jupydroid.app.ui.editor.SymbolRow] shows at all while a
   * cell is being typed into.
   *
   * There is no reference command to depart from here: `SymbolRow` is itself a phone-only
   * adaptation with no JupyterLab/IntelliJ counterpart (its own doc comment - "This is what
   * edit-mode keys mean on a phone"). **True by default**, so shipping this field changes no
   * pixel until a user turns it off - the same reasoning [codeWrapEnabled]'s own default carries.
   */
  val symbolRowShown: Boolean = true,
  /**
   * `docmanager:toggle-autosave` - whether a document's edits write themselves back, or wait to be
   * asked at close time (`packages/docmanager-extension/schema/plugin.json:113-118`,
   * `DocumentManager.autosave` at `packages/docmanager/src/manager.ts:96-126`).
   *
   * **True, the schema's own default**, and it governs the whole save path this app already had:
   * `DocumentAutosave`'s debounced write and its interval poll are both `SaveHandler`'s job, so
   * both stop here - the same "one flag governs the mechanism it names" shape [completionEnabled]
   * already is for `rememberCellCompletions`.
   */
  val autosaveEnabled: Boolean = true,
  /**
   * `autosaveInterval` - seconds between the periodic "write if dirty" sweep
   * (`packages/docmanager-extension/schema/plugin.json:119-124`,
   * `SaveHandler._minInterval` at `docmanager/src/savehandler.ts:21-43`).
   *
   * **The schema's own 120.** This app writes 500ms after a burst of edits already, so the poll's
   * real work is narrower than the reference's: it bounds how stale the file can get *while typing
   * never pauses* (a debounce without a gap never fires) and retries a write that failed - both
   * measured properties of `SaveHandler._save`, not conveniences. It is deliberately not the
   * debounce's own delay: mapping it there would have shipped the reference's 120 as a hundredfold
   * regression of the staleness bound a killable mobile process wants.
   */
  val autosaveIntervalSeconds: Int = AutosaveInterval.DEFAULT,
  /**
   * `kernelShutdown` - whether leaving a notebook ends its kernel rather than only closing the
   * socket (`packages/notebook-extension/schema/tracker.json:741-746`), read by the close the
   * way `SessionContext.dispose()` reads `kernelPreference.shutdownOnDispose`
   * (`packages/apputils/src/sessioncontext.tsx:690-698`): with it on, the ordinary close is the
   * shutdown `notebook:shutdown-kernel` otherwise is.
   *
   * **False, the schema's own default**, and the reference's reasoning survives on a phone: a
   * kernel is expensive to boot and cheap to keep, and the two-pane sweep closes documents the
   * user only glanced at - shipping it on would destroy state nobody asked to lose. The live
   * read also matches the reference, whose `updateTracker` pushes a flipped setting to widgets
   * already open (`notebook-extension/src/index.ts:2467-2477`).
   */
  val shutdownKernelOnClose: Boolean = false,
  /**
   * `indentUnit` - what one level of indentation is, a `Tab` or a number of spaces
   * (`packages/codemirror/src/extension.ts:707-720`, the schema's own five-value enum and `'4'`
   * default). One setting feeding three places: `PythonTypingRules`'s block-open level and the
   * symbol row's Tab key (which were both hardwired to four spaces), and the `options` a
   * `textDocument/rangeFormatting` is required to carry (`IndentUnit.formattingOptions`).
   *
   * **The reference's `tabSize` beside it is deliberately absent** - it is the display width of
   * a `\t`, which no Compose text field can express; see [IndentUnit]'s own doc.
   */
  val indentUnit: IndentUnit = IndentUnit.SPACES_4,
)

/**
 * Bounds measured on device against a real code cell rather than
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

/**
 * Bounds: the reference's `autosaveInterval` is an unbounded number in
 * a JSON schema editor, and a touch stepper needs edges. `MIN` of 5s is below which the poll is a
 * chore the 500ms debounce already does better; `MAX` of 600s is the point past which "autosave"
 * is a word for "writes when I close it", which [EditorSettings.autosaveEnabled] says directly.
 * `DEFAULT` is the schema's own 120 (`docmanager-extension/schema/plugin.json:119-124`).
 */
object AutosaveInterval {
  const val MIN = 5
  const val MAX = 600
  const val STEP = 5
  const val DEFAULT = 120
}
