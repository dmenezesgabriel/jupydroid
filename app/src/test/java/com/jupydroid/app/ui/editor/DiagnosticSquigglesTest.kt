package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The span a diagnostic's range underlines, after `markerDecorationsService`'s own
 * `_createDecorationRange` normalizations (`vscode/src/vs/editor/common/services/`):
 * an empty range grows to the word it sits in or one character at end-of-line, a
 * whole-line marker loses its leading indent, and a hint never spans lines - it renders
 * `start + 2`, "enough space for three dots" (the `dotdotdot` SVG the hint decoration is).
 */
class DiagnosticSquigglesTest {

  @Test
  fun aRealRange_isKeptVerbatim() {
    assertEquals(6 to 15, span("print(undef_name)", 6, 15, SEVERITY_ERROR))
  }

  @Test
  fun aRangePastTheText_isClampedToIt() {
    assertEquals(2 to 5, span("x = 1", 2, 99, SEVERITY_ERROR))
  }

  /**
   * Empty marker mid-line → the word under it: `model.getWordAtPosition` expanding the
   * zero-width range so something visible gets underlined (VS Code).
   */
  @Test
  fun anEmptyRangeMidLine_underlinesTheWordItSitsIn() {
    // "print(undef_name)" - an empty range inside undef_name takes the whole name.
    assertEquals(6 to 16, span("print(undef_name)", 10, 10, SEVERITY_ERROR))
  }

  /**
   * Empty marker at/after the last non-whitespace character - the "behind eol" case -
   * keeps its zero width and renders one character wide.
   */
  @Test
  fun anEmptyRangePastTheLastCodeChar_rendersOneCharWide() {
    assertEquals(5 to 6, span("x = 1   ", 5, 5, SEVERITY_WARNING))
    // A whitespace-only line has no word to grow into either - kept where it sits, 1ch wide.
    assertEquals(2 to 3, span("\n   \ny", 2, 2, SEVERITY_ERROR))
  }

  /**
   * A marker spanning its whole single line (VS Code's `startColumn == 1 &&
   * endColumn == MAX`) underlines from the first non-whitespace character, not the
   * indent - the squiggle belongs to the code, not the whitespace.
   */
  @Test
  fun aWholeLineRange_trimsTheIndent() {
    assertEquals(4 to 9, span("    x = 1", 0, 9, SEVERITY_ERROR))
  }

  /**
   * A whole-line marker on a whitespace-only line has no `minColumn` to trim to - VS Code
   * keeps the range as sent, indent and all.
   */
  @Test
  fun aWholeLineRange_onAWhitespaceOnlyLine_keepsTheRange() {
    assertEquals(0 to 6, span("      ", 0, 6, SEVERITY_ERROR))
  }

  /** A multi-line range is not the whole-line case - no trimming. */
  @Test
  fun aMultiLineRange_keepsItsLeadingWhitespace() {
    // (0,7) reaches into line 2's own characters - spans lines, not a whole-line marker.
    assertEquals(0 to 7, span("a = 1\nb = 2", 0, 7, SEVERITY_ERROR))
  }

  /** Hints collapse to `start + 2` on their own line - VS Code's three-dot rendering. */
  @Test
  fun aHint_neverSpansLines_andIsThreeDotsWide() {
    assertEquals(6 to 8, span("print(undef_name)", 6, 15, SEVERITY_HINT))
    assertEquals(6 to 8, span("print(undef_name)\nx = 1", 6, 19, SEVERITY_HINT))
  }

  @Test
  fun aHintAtLineEnd_isClampedToTheLine() {
    assertEquals(4 to 5, span("x = 1", 4, 4, SEVERITY_HINT))
  }

  private fun span(text: String, start: Int, end: Int, severity: Int): Pair<Int, Int>? =
    normalizeDiagnosticRange(text, start, end, severity)?.let { it.first to it.last + 1 }
}
