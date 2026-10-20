package com.jupydroid.extensions.ai.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** A key on Chat's row does to the question what the same key on the keyboard does. */
class ChatKeyRowTypingTest {
  @Test
  fun aKeyGoesInAtTheCaretAndTheCaretFollowsIt() {
    val question = TextFieldValue("ask about x", selection = TextRange(4))

    val typed = question.typedFromKeyRow("@")

    assertEquals(TextFieldValue("ask @about x", selection = TextRange(5)), typed)
  }

  @Test
  fun aKeyReplacesASelectionAsTheKeyboardsDoes() {
    val question = TextFieldValue("call foo now", selection = TextRange(5, 8))

    val typed = question.typedFromKeyRow("`")

    assertEquals(TextFieldValue("call ` now", selection = TextRange(6)), typed)
  }

  @Test
  fun aSelectionMadeRightToLeftIsReplacedToo() {
    val question = TextFieldValue("call foo now", selection = TextRange(8, 5))

    val typed = question.typedFromKeyRow("_")

    assertEquals(TextFieldValue("call _ now", selection = TextRange(6)), typed)
  }

  @Test
  fun theArrowsStepTheCaretAndStopAtEitherEnd() {
    val question = TextFieldValue("ab", selection = TextRange(1))

    assertEquals(TextRange(2), question.caretMovedBy(1).selection)
    assertEquals(TextRange(2), question.caretMovedBy(1).caretMovedBy(1).selection)
    assertEquals(TextRange(0), question.caretMovedBy(-1).caretMovedBy(-1).selection)
  }
}
