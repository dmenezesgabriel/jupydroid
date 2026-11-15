package com.jupydroid.app.ui.editor

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.MarginedAlertDialog

internal const val RENAME_SYMBOL_FIELD_TAG = "rename-symbol-field"

/**
 * The dialog "Rename symbol…" opens, `editor.action.rename`.
 *
 * VS Code's own surface is an inline input beside the symbol, prefill-selected and committed
 * on Enter (`renameWidget.ts`); a compact surface's equivalent is the dialog [RenameFileDialog]
 * already proves - autofocused field, the current name selected so typing replaces it, an
 * unchanged name a no-op rather than a no-op ask (`dialogs.ts`'s `newPath === fileName` rule).
 * The shipped `pylsp` has no `prepareRename`, so the prefill is [identifierSpanAt]'s own word
 * at the caret - VS Code's `getWordAtPosition` fallback exactly.
 *
 * [onConfirm] is called only with a name that changed and parses as a Python identifier; the
 * rest takes [onDismiss] or a disabled button, so no ask is ever made that could only fail.
 */
@Composable
internal fun RenameSymbolDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  // The whole identifier selected, not merely filled: typing should replace the symbol, the
  // same rule [RenameFileDialog] keeps for the file's stem.
  var name by remember(currentName) {
    mutableStateOf(TextFieldValue(currentName, TextRange(0, currentName.length)))
  }
  val field = remember { FocusRequester() }
  MarginedAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename symbol") },
    text = {
      // Refused, not rewritten - [RenameFileDialog]'s own rule: an identifier `a-b` can never
      // be jedi's answer to anything, so the error names the rule instead of asking anyway.
      val refused = name.text.isNotBlank() && !name.text.isPythonIdentifier()
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        isError = refused,
        supportingText = { if (refused) Text(INVALID_IDENTIFIER_MESSAGE) },
        modifier = Modifier
          .focusRequester(field)
          .semantics { testTagsAsResourceId = true }
          .testTag(RENAME_SYMBOL_FIELD_TAG),
        singleLine = true,
        label = { Text("New name") },
      )
      // [RenameFileDialog]'s own fix: without it the dialog opens with no caret and the first
      // keystroke goes nowhere. Inside the slot, for the reason it records there.
      LaunchedEffect(Unit) { field.requestFocus() }
    },
    confirmButton = {
      FocusRingTextButton(
        onClick = { if (name.text == currentName) onDismiss() else onConfirm(name.text) },
        enabled = name.text.isPythonIdentifier(),
      ) {
        Text("Rename")
      }
    },
    dismissButton = {
      FocusRingTextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

/**
 * The Python identifier under [offset] in [text], or null where the caret sits on none -
 * `getWordAtPosition` in the reference's `RenameInput` resolve, and the only "prepare" the
 * shipped `pylsp` needs doing locally. The caret's own edges both count: at a word's end the
 * word is still what is meant (`fo|o` and `foo|` both land on `foo`).
 *
 * ```
 * identifierSpanAt("def total(items):", 8)   // TextRange(4, 9) - "total"
 * identifierSpanAt("x = 1", 4)               // null - a literal is not a symbol
 * ```
 */
internal fun identifierSpanAt(text: String, offset: Int): TextRange? {
  var start = offset.coerceIn(0, text.length)
  var end = start
  while (start > 0 && text[start - 1].isIdentifierPart()) start--
  while (end < text.length && text[end].isIdentifierPart()) end++
  return TextRange(start, end).takeIf { start < end && text[start].isIdentifierStart() }
}

/**
 * The rename row's tap: [onIdentifier] when the frozen caret sat in an identifier - the
 * dialog opens on [text] - and [onMiss] otherwise, which is the reference's
 * `resolveRenameLocation` null: no input widget, just the "No result." the ask machine's
 * forwarded tap surfaces. Opening the dialog on a miss would offer a confirm that could only
 * ever fail, the ask being the caret's position and not the typed name.
 */
internal fun renameSymbolTapped(
  text: CellEditorText,
  onIdentifier: (CellEditorText) -> Unit,
  onMiss: (CellEditorText) -> Unit,
) {
  if (identifierSpanAt(text.source, text.caret.end) != null) onIdentifier(text) else onMiss(text)
}

/** `[A-Za-z_][A-Za-z0-9_]*` in [Char] halves - jedi's own alphabet for what it will rename. */
internal fun String.isPythonIdentifier(): Boolean =
  isNotEmpty() && first().isIdentifierStart() && all(Char::isIdentifierPart)

private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_'

private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'

/** The rule named rather than the button merely greyed - [RenameFileDialog]'s own wording rule. */
private const val INVALID_IDENTIFIER_MESSAGE =
  "A name must start with a letter or _, and hold only letters, digits, and _"
