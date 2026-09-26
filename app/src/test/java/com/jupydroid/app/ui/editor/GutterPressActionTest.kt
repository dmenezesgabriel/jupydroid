package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **P5e-1: which action a gutter press resolves to.**
 *
 * The gutter holds two affordances in one column now: the breakpoint toggle VS Code's
 * `onMouseDown` always had, and the diagnostic mark VS Code's `markerHoverParticipant` answers
 * on hover - a hover mobile performs as a tap on the mark itself. So a press resolves by where
 * it landed and how long it held: the mark's own column on a diagnosed line shows the detail,
 * an armed line's long-press is still the condition editor (P6a-16's contract, never yielded),
 * and anything else toggles a breakpoint as it always did.
 */
class GutterPressActionTest {
  @Test
  fun `a press on the mark column of a diagnosed line shows the detail`() {
    assertEquals(
      GutterPress.SHOW_DIAGNOSTICS,
      gutterPress(armed = false, held = false, onMark = true, diagnosed = true),
    )
  }

  @Test
  fun `a press on the mark column of a clean line still arms it`() {
    assertEquals(
      GutterPress.TOGGLE_BREAKPOINT,
      gutterPress(armed = false, held = false, onMark = true, diagnosed = false),
    )
  }

  @Test
  fun `a press on the numbers of a diagnosed line still arms it`() {
    assertEquals(
      GutterPress.TOGGLE_BREAKPOINT,
      gutterPress(armed = false, held = false, onMark = false, diagnosed = true),
    )
  }

  @Test
  fun `a held press on an armed line is the condition editor, even on the mark`() {
    assertEquals(
      GutterPress.EDIT_CONDITION,
      gutterPress(armed = true, held = true, onMark = true, diagnosed = true),
    )
  }

  @Test
  fun `a held press on the mark of a diagnosed unarmed line shows the detail`() {
    assertEquals(
      GutterPress.SHOW_DIAGNOSTICS,
      gutterPress(armed = false, held = true, onMark = true, diagnosed = true),
    )
  }

  @Test
  fun `a held press elsewhere still arms`() {
    assertEquals(
      GutterPress.TOGGLE_BREAKPOINT,
      gutterPress(armed = false, held = true, onMark = false, diagnosed = false),
    )
  }

  /** A diagnostic row's tail - `pyflakes · E305`, either alone, or nothing when neither came. */
  @Test
  fun `a diagnostic's attribution joins source and code, tolerating either missing`() {
    val diagnostic =
      CellDiagnostic(line = 1, severity = SEVERITY_ERROR, code = "E305", source = "pyflakes", message = "m")
    assertEquals("pyflakes · E305", attributionOf(diagnostic))
    assertEquals("pyflakes", attributionOf(diagnostic.copy(code = null)))
    assertNull(attributionOf(diagnostic.copy(source = null, code = null)))
  }
}
