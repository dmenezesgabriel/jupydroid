package com.jupydroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.SgrRun
import com.jupydroid.app.terminal.TerminalPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What an output's escapes draw as: VS Code's notebook renderer (vscode@3f3380c6
 * `extensions/notebook-renderers/src/ansi.ts`, and the `.code-*` rules in `src/index.ts`).
 */
class OutputTextColoursTest {
  private val palette = TerminalPalette.VS_CODE_DARK
  private val fg = CellStyle.COLOR_INDEX_FOREGROUND
  private val bg = CellStyle.COLOR_INDEX_BACKGROUND

  private fun spanOf(fore: Int, back: Int = bg, effect: Int = 0): SpanStyle =
    CellStyle.encode(fore, back, effect).outputSpanStyle(palette)

  @Test
  fun outputSpanStyle_paintsAnIndexWithThePalettesColour() {
    assertEquals(SpanStyle(color = Color(palette.at(1))), spanOf(1))
  }

  /** The output's own text colour - `onSurface`, or the error tint's - is the default, not the terminal's. */
  @Test
  fun outputSpanStyle_leavesTheDefaultColoursToTheOutput() {
    assertEquals(SpanStyle(), spanOf(fg))
  }

  /** `.code-bold` is `font-weight: bold` and nothing else: VS Code does not brighten, as a terminal does. */
  @Test
  fun outputSpanStyle_drawsBoldAsWeightAndKeepsItsColour() {
    assertEquals(
      SpanStyle(color = Color(palette.at(2)), fontWeight = FontWeight.Bold),
      spanOf(2, effect = CellStyle.BOLD),
    )
  }

  @Test
  fun outputSpanStyle_takesATruecolourAsItIs() {
    assertEquals(SpanStyle(color = Color(0xFF102030.toInt())), spanOf(0xFF102030.toInt()))
  }

  @Test
  fun outputSpanStyle_paintsABackground() {
    assertEquals(SpanStyle(background = Color(palette.at(4))), spanOf(fg, back = 4))
  }

  /** `reverseForegroundAndBackgroundColors` swaps what is set; a default on either side stays the output's. */
  @Test
  fun outputSpanStyle_swapsTheColoursOfAnInverseRun() {
    assertEquals(SpanStyle(background = Color(palette.at(1))), spanOf(1, effect = CellStyle.INVERSE))
  }

  @Test
  fun outputSpanStyle_drawsItalicUnderlineAndStrikeThrough() {
    val effect = CellStyle.ITALIC or CellStyle.UNDERLINE or CellStyle.STRIKETHROUGH
    val expected = SpanStyle(
      fontStyle = FontStyle.Italic,
      textDecoration = TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)),
    )

    assertEquals(expected, spanOf(fg, effect = effect))
  }

  /** Dim, blink and hidden have classes in `ansi.ts` and no rule in `index.ts`'s stylesheet. */
  @Test
  fun outputSpanStyle_drawsNothingForDimBlinkOrHidden() {
    assertEquals(SpanStyle(), spanOf(fg, effect = CellStyle.DIM or CellStyle.BLINK or CellStyle.INVISIBLE))
  }

  @Test
  fun coloured_laysEachRunOverItsStretchOfText() {
    val red = CellStyle.encode(1, bg, 0)
    val output = RenderedOutput.Text("a red b", isError = false, runs = listOf(SgrRun(2, 5, red)))

    val coloured = output.coloured(palette)

    assertEquals("a red b", coloured.text)
    assertEquals(listOf(AnnotatedString.Range(SpanStyle(color = Color(palette.at(1))), 2, 5)), coloured.spanStyles)
  }

  @Test
  fun frame_leavesOrdinaryOutputUnframed() {
    assertEquals(OutputTextFrame.PLAIN, RenderedOutput.Text("42", isError = false).frame())
  }

  @Test
  fun frame_tintsAnErrorThatCarriesNoColour() {
    assertEquals(OutputTextFrame.TINTED, RenderedOutput.Text("ValueError: bad", isError = true).frame())
  }

  /**
   * A coloured traceback draws on the surface, as VS Code draws it: ANSI red `#CD3131` on the dark
   * error tint `#3A1D1D` is 2.97:1, and on the dark surface 3.61:1.
   */
  @Test
  fun frame_rulesAnErrorThatCarriesColourInsteadOfTintingIt() {
    val traceback = RenderedOutput.Text(
      "ZeroDivisionError",
      isError = true,
      runs = listOf(SgrRun(0, 17, CellStyle.encode(1, bg, 0))),
    )

    assertEquals(OutputTextFrame.RULED, traceback.frame())
  }

  /** A find over a coloured traceback marks the match and keeps the colour under it. */
  @Test
  fun withSearchMatches_laysTheMatchOverTheColoursRatherThanReplacingThem() {
    val red = SpanStyle(color = Color(palette.at(1)))
    val match = SpanStyle(background = Color.Yellow)
    val output = RenderedOutput.Text(
      "a red b",
      isError = false,
      runs = listOf(SgrRun(2, 5, CellStyle.encode(1, bg, 0))),
    )

    val marked = output.coloured(palette)
      .withSearchMatches(CellSearchHighlight(matches = listOf(2..4)), SearchMatchStyles(match, match))

    assertEquals(
      listOf(AnnotatedString.Range(red, 2, 5), AnnotatedString.Range(match, 2, 5)),
      marked.spanStyles,
    )
  }
}
