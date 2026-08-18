package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The app-wide editor preferences P6c grows - one field today.
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
@Serializable
data class EditorSettings(val autoClosingBrackets: Boolean = true)
