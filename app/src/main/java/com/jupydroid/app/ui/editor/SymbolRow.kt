package com.jupydroid.app.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/**
 * The row of characters a Python keyboard does not have, above the soft keyboard.
 *
 * This is what edit-mode keys mean on a phone. JupyterLab and IntelliJ both hang a great deal on
 * keys a touch keyboard simply lacks - and the ones a *code* editor needs most are not letters at
 * all: `:` ends every block header, brackets and quotes come in pairs, `#` starts every comment,
 * and Tab is how a line gets its indent. On a soft keyboard each of those is two taps away behind
 * a symbols page.
 *
 * Shown only in [CellMode.EDIT], because there is nothing to type into otherwise, and it would
 * cost a row of the notebook to say so.
 *
 * `imePadding` puts it on top of the keyboard; `navigationBarsPadding` keeps it clear of the
 * gesture bar in the moment between the keyboard closing and the mode changing.
 *
 * ```
 * if (state.mode == CellMode.EDIT) SymbolRow(input = focusedCellInput)
 * ```
 */
@Composable
internal fun SymbolRow(input: FocusedCellInput, modifier: Modifier = Modifier) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        // Scrolls rather than wraps or shrinks: thirteen keys do not fit 320dp at a size worth
        // tapping, and a Row keeps every one in the semantics tree where a LazyRow would drop
        // the off-screen ones - which is what a screen reader and a test both walk.
        .horizontalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding(),
    ) {
      SymbolKey(label = "Tab", onClick = input::indent)
      TYPED_KEYS.forEach { key -> SymbolKey(label = key, onClick = { input.type(key) }) }
      SymbolKey(label = "←", onClick = { input.moveCaret(-1) })
      SymbolKey(label = "→", onClick = { input.moveCaret(1) })
    }
  }
}

/**
 * A `TextButton` rather than a hand-rolled box: it carries Material's 48dp minimum touch target,
 * which is the one thing every key here has to have.
 */
@Composable
private fun SymbolKey(label: String, onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text(text = label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
  }
}

/**
 * Ordered by how often Python reaches for them, not by where a keyboard hides them: the colon
 * ends every block header, the pairs come next, then the comment hash and the underscore that
 * every snake_case name needs.
 */
private val TYPED_KEYS = listOf(":", "(", ")", "[", "]", "{", "}", "'", "\"", "=", "#", "_")
