package com.jupydroid.app.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Warning
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mark's glyph is the severity, borrowed from VS Code's codicon vocabulary rather than
 * invented: `error` is its circle-x, `warning` its triangle, `info` its circle-i and a hint the
 * `lightbulb` quick-fix wears. Unknown severities read as the default, error - the same
 * fallback [severityColour] already uses.
 */
class DiagnosticMarksTest {
  @Test
  fun eachSeverityNamesItsGlyph() {
    assertEquals(Icons.Outlined.Cancel, severityIcon(SEVERITY_ERROR))
    assertEquals(Icons.Outlined.Warning, severityIcon(SEVERITY_WARNING))
    assertEquals(Icons.Outlined.Info, severityIcon(SEVERITY_INFORMATION))
    assertEquals(Icons.Outlined.Lightbulb, severityIcon(SEVERITY_HINT))
  }

  @Test
  fun anUnknownSeverityReadsAsError() {
    assertEquals(Icons.Outlined.Cancel, severityIcon(99))
  }
}
