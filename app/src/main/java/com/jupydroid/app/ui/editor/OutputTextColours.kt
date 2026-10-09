package com.jupydroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.ui.terminal.resolvedColor
import com.jupydroid.app.ui.terminal.textDecorationFor

/**
 * This output's text with the colours its escapes asked for laid over it.
 *
 * A function of the palette rather than of the theme, so the choice of light or dark stays in the
 * composable and this stays testable on the JVM.
 *
 * ```
 * output.coloured(TerminalPalette.VS_CODE_DARK)
 * ```
 */
fun RenderedOutput.Text.coloured(palette: TerminalPalette): AnnotatedString {
  if (runs.isEmpty()) return AnnotatedString(text)
  return AnnotatedString(text, runs.map { AnnotatedString.Range(it.style.outputSpanStyle(palette), it.start, it.end) })
}

/**
 * What one SGR style draws as in an output - VS Code's notebook renderer, which is not the
 * terminal's (vscode@3f3380c6 `extensions/notebook-renderers/src/ansi.ts`, and the `.code-*` rules
 * in `src/index.ts`):
 *
 * - **Bold is weight only.** `.code-bold` sets `font-weight` and the colour stays the one asked for,
 *   where a terminal promotes a bold 0-7 to its bright twin.
 * - **The default colours are the output's own**, so an unset colour draws no span: `onSurface` or
 *   the error tint shows through, rather than the terminal's foreground.
 * - **Inverse swaps what is set**, as `reverseForegroundAndBackgroundColors` does, so a red
 *   foreground becomes a red background and the text keeps the output's colour.
 * - **Dim, blink and hidden draw nothing**: `ansi.ts` gives them classes and the stylesheet no rule.
 */
internal fun CellStyle.outputSpanStyle(palette: TerminalPalette): SpanStyle {
  val inverse = effect and CellStyle.INVERSE != 0
  return SpanStyle(
    color = outputColor(if (inverse) backColor else foreColor, palette),
    background = outputColor(if (inverse) foreColor else backColor, palette),
    fontWeight = FontWeight.Bold.takeIf { effect and CellStyle.BOLD != 0 },
    fontStyle = FontStyle.Italic.takeIf { effect and CellStyle.ITALIC != 0 },
    textDecoration = textDecorationFor(effect),
  )
}

private fun outputColor(indexOrArgb: Int, palette: TerminalPalette): Color =
  if (indexOrArgb in TERMINAL_OWN_COLORS) Color.Unspecified else Color(resolvedColor(indexOrArgb, palette::at))

/** The three entries a terminal names for itself; in an output they mean "the output's own". */
private val TERMINAL_OWN_COLORS = CellStyle.COLOR_INDEX_FOREGROUND..CellStyle.COLOR_INDEX_CURSOR
