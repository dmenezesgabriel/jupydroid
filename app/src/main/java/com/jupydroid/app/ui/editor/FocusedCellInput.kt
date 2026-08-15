package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange

/**
 * The cell the symbol row types into: whichever one has the caret.
 *
 * The row is one per screen and sits above the keyboard, outside every cell, so it needs a way to
 * reach the focused field. A field registers itself here on focus and unregisters on blur, which
 * keeps `TextFieldState` where it belongs - inside the field - instead of hoisting it into the
 * ViewModel to be shared.
 *
 * ```
 * val input = remember { FocusedCellInput() }
 * CodeCellField(..., input = input)          // attaches itself while focused
 * SymbolRow(input = input)                   // types into whatever is attached
 * ```
 */
class FocusedCellInput {
  private var target by mutableStateOf<TextFieldState?>(null)

  /** Whether any cell is focused, which is what decides if the row can do anything. */
  val isAttached: Boolean get() = target != null

  fun attach(state: TextFieldState) {
    target = state
  }

  /**
   * Unhooks [state], and only [state].
   *
   * Guarded by identity for the same reason `EditorViewModel.cellFocusChanged` is: focus reports
   * arrive in no guaranteed order, so a stale "I lost focus" from the cell being left would
   * otherwise unhook the one that just gained it.
   */
  fun detach(state: TextFieldState) {
    if (target === state) target = null
  }

  /**
   * Types [text] exactly as the soft keyboard would, [PythonTypingRules] and all.
   *
   * The rules are applied by hand because a programmatic `edit` does **not** run the field's
   * `InputTransformation`: without this, `(` from the row would insert a lone bracket where the
   * same key on the keyboard gives `()`, and the two would disagree about one character.
   */
  fun type(text: String) {
    val state = target ?: return
    state.edit {
      insert(selection.start, text)
      with(rules) { transformInput() }
    }
  }

  /** One indent level, which the rules define as four spaces on a space-indented line. */
  fun indent() {
    val state = target ?: return
    state.edit { insert(selection.start, FOUR_SPACES) }
  }

  /** Steps the caret along the text, stopping at either end - a caret outside the text throws. */
  fun moveCaret(by: Int) {
    val state = target ?: return
    state.edit {
      selection = TextRange((selection.start + by).coerceIn(0, length))
    }
  }

  private companion object {
    val rules = PythonTypingRules()
    const val FOUR_SPACES = "    "
  }
}

/**
 * Registers [state] with the row for as long as the field holding it has the caret.
 *
 * Shared by both fields rather than written twice, and that is not tidiness: the markdown field
 * went without it, so the row sat above the keyboard in a markdown cell doing **nothing** - the
 * exact "a control that lights up and does not work" this codebase refuses elsewhere. It surfaced
 * because P2b's markdown flow drives a caret with `←`, and the split then cut at the end of the
 * text instead of where the flow had put it.
 *
 * Detach is guarded by identity inside [FocusedCellInput.detach], so a cell being left cannot
 * unhook the one just entered.
 */
@Composable
internal fun FocusedCellInput?.attachWhileFocused(state: TextFieldState, focused: Boolean) {
  val input = this ?: return
  DisposableEffect(input, state, focused) {
    if (focused) input.attach(state)
    onDispose { input.detach(state) }
  }
}
