package com.jupydroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** The colours the page styles itself with, so CodeMirror matches the app rather than guessing. */
data class EditorTheme(val background: Color, val foreground: Color, val gutter: Color) {
  fun tokens(): Map<String, String> = mapOf(
    "background" to background.hex(),
    "foreground" to foreground.hex(),
    "gutter" to gutter.hex(),
  )

  private fun Color.hex(): String = String.format("#%06X", toArgb() and WHITE)

  private companion object {
    const val WHITE = 0xFFFFFF
  }
}
