package com.jupydroid.app.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The colours the editor page styles itself with.
 *
 * Every one is sent across as a CSS variable, including the syntax-token colours, so
 * CodeMirror follows the app's theme instead of carrying its own. That is not cosmetic:
 * CodeMirror's default highlight style is built for light backgrounds and renders numbers and
 * comments nearly invisibly on the app's dark theme - visible in `evidence/step5.4/`.
 */
data class EditorTheme(
  val background: Color,
  val foreground: Color,
  val gutter: Color,
  val keyword: Color,
  val string: Color,
  val number: Color,
  val comment: Color,
  val function: Color,
) {
  fun tokens(): Map<String, String> = mapOf(
    "background" to background.hex(),
    "foreground" to foreground.hex(),
    "gutter" to gutter.hex(),
    "keyword" to keyword.hex(),
    "string" to string.hex(),
    "number" to number.hex(),
    "comment" to comment.hex(),
    "function" to function.hex(),
  )

  private fun Color.hex(): String = String.format("#%06X", toArgb() and WHITE)

  private companion object {
    const val WHITE = 0xFFFFFF
  }
}

/**
 * Derived from the Material scheme, so light and dark both work without a second palette to
 * keep in step. Only the accents are chosen here; everything structural is the scheme's.
 */
@Composable
@ReadOnlyComposable
fun cellEditorTheme(): EditorTheme = EditorTheme(
  background = MaterialTheme.colorScheme.surfaceVariant,
  foreground = MaterialTheme.colorScheme.onSurfaceVariant,
  gutter = MaterialTheme.colorScheme.outline,
  keyword = MaterialTheme.colorScheme.primary,
  string = MaterialTheme.colorScheme.tertiary,
  number = MaterialTheme.colorScheme.secondary,
  comment = MaterialTheme.colorScheme.outline,
  function = MaterialTheme.colorScheme.primary,
)
