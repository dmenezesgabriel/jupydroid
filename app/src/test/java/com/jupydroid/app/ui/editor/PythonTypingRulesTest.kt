package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drives the rules through a real [TextFieldState], because the whole slice is about what the
 * buffer looks like after a keystroke - a hand-built fake of `changes` would only prove that the
 * fake agrees with itself.
 *
 * `|` marks the cursor on both sides of every assertion.
 */
class PythonTypingRulesTest {
  private val rules = PythonTypingRules()

  @Test
  fun newline_carriesTheIndentOfTheLineItBreaks() {
    assertEquals("    x = 1\n    |", typing("    x = 1|", "\n"))
  }

  @Test
  fun newline_addsALevelAfterALineThatOpensABlock() {
    assertEquals("if x:\n    |", typing("if x:|", "\n"))
  }

  @Test
  fun newline_addsThatLevelOnTopOfTheIndentAlreadyThere() {
    assertEquals("    if x:\n        |", typing("    if x:|", "\n"))
  }

  @Test
  fun newline_findsTheColonPastTrailingSpaces() {
    assertEquals("if x:  \n    |", typing("if x:  |", "\n"))
  }

  @Test
  fun newline_addsNothingAtColumnZero() {
    assertEquals("x = 1\n|", typing("x = 1|", "\n"))
  }

  @Test
  fun newline_indentsTheTailWhenItSplitsALine() {
    assertEquals("    a\n    |b", typing("    a|b", "\n"))
  }

  /** Four spaces under a tab is a `TabError`, not a style disagreement. */
  @Test
  fun newline_indentsWithATabWhenTheLineDoes() {
    assertEquals("\tif x:\n\t\t|", typing("\tif x:|", "\n"))
  }

  @Test
  fun bracket_closesItself() {
    assertEquals("print(|)", typing("print|", "("))
    assertEquals("xs[|]", typing("xs|", "["))
    assertEquals("d = {|}", typing("d = |", "{"))
  }

  @Test
  fun quote_closesItself() {
    assertEquals("x = \"|\"", typing("x = |", "\""))
    assertEquals("x = '|'", typing("x = |", "'"))
  }

  /** An f-string prefix is a letter right before the quote, which must not block the pair. */
  @Test
  fun quote_closesAfterAnFStringPrefix() {
    assertEquals("x = f'|'", typing("x = f|", "'"))
  }

  @Test
  fun bracket_doesNotCloseInFrontOfAWord() {
    assertEquals("(|value", typing("|value", "("))
  }

  @Test
  fun bracket_closesInFrontOfSomethingThatAlreadyCloses() {
    assertEquals("f([|])", typing("f(|)", "["))
  }

  @Test
  fun closer_stepsOverThePartnerInsteadOfDoublingIt() {
    assertEquals("print()|", typing("print(|)", ")"))
  }

  @Test
  fun quote_stepsOverItsPartner() {
    assertEquals("x = 'a'|", typing("x = 'a|'", "'"))
  }

  /** Two quotes then a third is someone opening a docstring, not closing an empty string. */
  @Test
  fun quote_doesNotCloseWhileATripleQuoteIsBeingTyped() {
    assertEquals("s = '''|", typing("s = ''|", "'"))
  }

  @Test
  fun closer_isTypedNormallyWhenNothingMatchesIt() {
    assertEquals("f(a)|", typing("f(a|", ")"))
  }

  @Test
  fun ordinaryCharacter_isLeftAlone() {
    assertEquals("xy|", typing("x|", "y"))
  }

  @Test
  fun paste_isLeftAlone() {
    assertEquals("x = (1, 2)|", typing("x = |", "(1, 2)"))
  }

  /**
   * Wrapping a selection in brackets is a different feature; until it exists, replacing one must
   * not half-do it by leaving an unmatched closer behind.
   */
  @Test
  fun replacingASelection_isLeftAlone() {
    val state = TextFieldState("say hello", TextRange(4, 9))
    state.edit {
      replace(4, 9, "\"")
      with(rules) { transformInput() }
    }

    assertEquals("say \"", state.text.toString())
  }

  // --- notebook:toggle-autoclosing-brackets, universal (P2g-3) -----------------------------

  /**
   * `autoClosingBrackets = false` gates [closePair] and [stepOverPartner] only.
   * [continueIndent] is a different CodeMirror setting there and must not move with it - a Python
   * cell that closes nothing must still carry a block's indent onto the next line.
   */
  @Test
  fun bracket_doesNotCloseWhenTheSettingIsOff() {
    assertEquals("print(|", typing("print|", "(", autoClosingBrackets = false))
  }

  @Test
  fun closer_doesNotStepOverWhenTheSettingIsOff() {
    assertEquals("print()|)", typing("print(|)", ")", autoClosingBrackets = false))
  }

  @Test
  fun newline_stillCarriesTheIndentWhenTheSettingIsOff() {
    assertEquals("if x:\n    |", typing("if x:|", "\n", autoClosingBrackets = false))
  }

  private fun typing(before: String, typed: String, autoClosingBrackets: Boolean = true): String {
    val cursor = before.indexOf(CURSOR)
    require(cursor >= 0) { "no $CURSOR in \"$before\"" }
    val state = TextFieldState(before.replace(CURSOR, ""), TextRange(cursor))
    val active = if (autoClosingBrackets) rules else PythonTypingRules(autoClosingBrackets = false)
    state.edit {
      insert(selection.start, typed)
      with(active) { transformInput() }
    }
    val text = state.text.toString()
    return text.substring(0, state.selection.start) + CURSOR + text.substring(state.selection.start)
  }

  private companion object {
    const val CURSOR = "|"
  }
}
