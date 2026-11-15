package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **Which action a gutter press resolves to.**
 *
 * The gutter is the breakpoint's alone, as VS Code's glyph margin is
 * (`breakpointEditorContribution.ts` at `vscode@3f3380c668d0`): a finding is read from its squiggle,
 * never from here. So a press resolves by how long it held and whether the line already carries
 * a breakpoint - armed and held is the condition editor, anything else toggles.
 */
class GutterPressActionTest {
  @Test
  fun `a held press on an armed line is the condition editor`() {
    assertEquals(GutterPress.EDIT_CONDITION, gutterPress(armed = true, held = true))
  }

  @Test
  fun `a quick press on an armed line removes it`() {
    assertEquals(GutterPress.TOGGLE_BREAKPOINT, gutterPress(armed = true, held = false))
  }

  @Test
  fun `a press on a bare line arms it, held or not`() {
    assertEquals(GutterPress.TOGGLE_BREAKPOINT, gutterPress(armed = false, held = false))
    assertEquals(GutterPress.TOGGLE_BREAKPOINT, gutterPress(armed = false, held = true))
  }

  /** A diagnostic row's tail - `pyflakes · E305`, either alone, or nothing when neither came. */
  @Test
  fun `a diagnostic's attribution joins source and code, tolerating either missing`() {
    val diagnostic =
      CellDiagnostic(
        line = 1,
        start = 0,
        end = 1,
        severity = SEVERITY_ERROR,
        code = "E305",
        source = "pyflakes",
        message = "m",
      )
    assertEquals("pyflakes · E305", attributionOf(diagnostic))
    assertEquals("pyflakes", attributionOf(diagnostic.copy(code = null)))
    assertNull(attributionOf(diagnostic.copy(source = null, code = null)))
  }
}
