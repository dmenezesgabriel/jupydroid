package com.jupydroid.app.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Where on a line a finding can be read.** The squiggle's own span is the target, as VS Code's
 * marker hover is (`markerHoverParticipant.ts` at `vscode@3f3380c668d0`), widened sideways to a
 * finger's width because a hint's span is two characters - 16.5dp - and a row is too short to
 * widen the other way without taking the next line's taps.
 */
class DiagnosticTapHitTest {
  private val narrowSpan = DiagnosticSpanRect(line = 2, rect = Rect(100f, 18f, 116f, 36f))

  @Test
  fun `a tap inside a span reports its line`() {
    assertEquals(2, diagnosticLineAt(listOf(narrowSpan), Offset(108f, 27f), minWidthPx = 48f))
  }

  @Test
  fun `a tap beside a narrow span reports its line once the span is widened to a finger`() {
    // 16 wide widened to 48 reaches 16px each side of the span.
    assertEquals(2, diagnosticLineAt(listOf(narrowSpan), Offset(88f, 27f), minWidthPx = 48f))
    assertEquals(2, diagnosticLineAt(listOf(narrowSpan), Offset(128f, 27f), minWidthPx = 48f))
  }

  @Test
  fun `a tap past the widened span reports nothing`() {
    assertNull(diagnosticLineAt(listOf(narrowSpan), Offset(80f, 27f), minWidthPx = 48f))
    assertNull(diagnosticLineAt(listOf(narrowSpan), Offset(140f, 27f), minWidthPx = 48f))
  }

  @Test
  fun `a tap on another row reports nothing`() {
    assertNull(diagnosticLineAt(listOf(narrowSpan), Offset(108f, 9f), minWidthPx = 48f))
    assertNull(diagnosticLineAt(listOf(narrowSpan), Offset(108f, 45f), minWidthPx = 48f))
  }

  @Test
  fun `a span already wider than a finger keeps its own width`() {
    val wide = DiagnosticSpanRect(line = 1, rect = Rect(0f, 0f, 200f, 18f))
    assertEquals(1, diagnosticLineAt(listOf(wide), Offset(199f, 9f), minWidthPx = 48f))
    assertNull(diagnosticLineAt(listOf(wide), Offset(201f, 9f), minWidthPx = 48f))
  }

  @Test
  fun `no spans report nothing`() {
    assertNull(diagnosticLineAt(emptyList(), Offset(0f, 0f), minWidthPx = 48f))
  }
}
