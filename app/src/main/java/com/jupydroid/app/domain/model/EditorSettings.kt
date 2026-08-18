package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide editor preferences P6c grows - four fields today.
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
)

