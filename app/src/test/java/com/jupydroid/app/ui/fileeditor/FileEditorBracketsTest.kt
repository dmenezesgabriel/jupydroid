package com.jupydroid.app.ui.fileeditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which files close a bracket as it is opened - one answer read by the field's own typing rules and
 * by the symbol row, so a `(` from either gives the same text.
 */
class FileEditorBracketsTest {
  @Test
  fun aPythonFileClosesBracketsWhileTheSettingIsOn() {
    assertTrue(autoClosesBrackets("analysis.py", autoClosingBrackets = true))
  }

  @Test
  fun anUppercaseExtensionIsTheSameFileKind() {
    assertTrue(autoClosesBrackets("CONFIG.JSON", autoClosingBrackets = true))
  }

  @Test
  fun proseNeverClosesBrackets() {
    assertFalse(autoClosesBrackets("notes.md", autoClosingBrackets = true))
  }

  @Test
  fun theSettingOffClosesNothingEvenInPython() {
    assertFalse(autoClosesBrackets("analysis.py", autoClosingBrackets = false))
  }
}
