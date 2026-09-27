package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `identifierSpanAt` - the rename dialog's prefill and `getWordAtPosition` locally, because the
 * shipped `pylsp` has no `prepareRename` to ask instead. The word at the caret is what the
 * dialog opens with, and the caret's edges both count: a caret leaning on either side of a name
 * still means that name.
 */
class IdentifierSpanAtTest {

  @Test
  fun aCaretInsideAWord_answersTheWholeWord() {
    assertEquals(TextRange(4, 9), identifierSpanAt("def total(items):", 6))
  }

  @Test
  fun aCaretOnAWordEdge_answersTheWordItLeansOn() {
    assertEquals(TextRange(4, 9), identifierSpanAt("def total(items):", 4))
    assertEquals(TextRange(4, 9), identifierSpanAt("def total(items):", 9))
  }

  @Test
  fun aCaretOnPunctuation_answersNothing() {
    assertNull(identifierSpanAt("x = (1)", 4))
    assertNull(identifierSpanAt("  ", 1))
    assertNull(identifierSpanAt("", 0))
  }

  /** A word that starts with a digit is a literal, not a symbol jedi could rename. */
  @Test
  fun aNumberLikeWord_answersNothing() {
    assertNull(identifierSpanAt("x = 123abc", 6))
  }

  @Test
  fun aDottedName_answersOnlyThePartUnderTheCaret() {
    // `os.path.join` asks for the segment the caret touches, which is the one jedi renames.
    assertEquals(TextRange(10, 14), identifierSpanAt("import os.path.join", 11))
  }

  @Test
  fun aCaretPastTheEnd_clampsIntoTheLine() {
    assertEquals(TextRange(0, 3), identifierSpanAt("foo", 10))
  }

  @Test
  fun anIdentifierShapeIsWhatTheDialogAccepts() {
    assertEquals(true, "_private".isPythonIdentifier())
    assertEquals(true, "print2".isPythonIdentifier())
    assertEquals(false, "".isPythonIdentifier())
    assertEquals(false, "2fa".isPythonIdentifier())
    assertEquals(false, "a-b".isPythonIdentifier())
    assertEquals(false, "a b".isPythonIdentifier())
  }
}
