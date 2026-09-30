package com.jupydroid.app.ui.editor

import com.jupydroid.app.syntax.SyntaxSpan
import com.jupydroid.app.syntax.insideUnclosedParen

/** The code and caret a signature-help ask was made about, kept so a stale answer can be recognised. */
data class SignatureHelpQuery(val code: String, val cursorOffset: Int)

/**
 * What to ask the language server about, or null when the caret is nowhere worth asking
 * about, VS Code's `parameterHintsModel` semantics.
 *
 * While the hints are hidden only a *typed* trigger char asks - `onTypeHint` checks the last
 * char of the inserted text, which is why [typedChar] rides beside the caret rather than
 * being read off the code: `join(path, |` asks only if the comma, not the space, was just
 * typed. pylsp's measured `triggerCharacters` are `(`, `,`, `=` (`LspSurfaceProbeTest`).
 * Once the model is triggered - a request in flight or the hints already up, `isTriggered`
 * covering both - `onModelContentChange` retriggers on every content change and
 * `onCursorChange` on a caret move. [armed] is that state, and either way the caret
 * must sit inside a call still open: [insideUnclosedParen]'s answer, which the literal-aware
 * scan keeps honest where `f(")", arg|` would otherwise read closed.
 *
 * ```
 * signatureHelpQueryAt("join(", 5, spans, armed = false, typedChar = '(')
 *   // SignatureHelpQuery("join(", 5)
 * ```
 */
fun signatureHelpQueryAt(
  code: String,
  cursor: Int,
  spans: List<SyntaxSpan>,
  armed: Boolean,
  typedChar: Char?,
): SignatureHelpQuery? {
  val asks = armed || typedChar in TRIGGER_CHARS
  return if (asks && insideUnclosedParen(code, cursor, spans)) SignatureHelpQuery(code, cursor) else null
}

/** pylsp's advertised `triggerCharacters` - measured on device by `LspSurfaceProbeTest`. */
private val TRIGGER_CHARS = setOf('(', ',', '=')
