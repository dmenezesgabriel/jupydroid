package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.LanguageParameter
import com.jupydroid.app.domain.port.LanguageSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two pure helpers the signature-help pipeline leans on (`docs/PLAN.md` P5e-4):
 *
 * - [typedCharBetween], which recovers the char a keystroke inserted - what
 *   `parameterHintsModel.onTypeHint` reads off the typed text - including the `(` of an
 *   auto-closed `()` pair, which is the keystroke that opens most calls.
 * - [parameterLabelOffsets], VS Code's `getParameterLabelOffsets` verbatim: a `[start, end]`
 *   wire pair used as-is, else the first `(\W|^)label(?=\W|$)` match, else no highlight.
 */
class CellSignatureHelpTest {

  /** A single char typed where the caret lands is the typed char. */
  @Test
  fun aTypedChar_isReadOffTheInsertion() {
    assertEquals(',', typedCharBetween("join(path", "join(path,", caret = 10))
    assertEquals(' ', typedCharBetween("join(path,", "join(path, ", caret = 11))
  }

  /**
   * Auto-close turns a `(` keystroke into a `()` insertion with the caret between them - and
   * the typed char is still the `(`, the trigger that opens almost every ask.
   */
  @Test
  fun anAutoClosedParen_isStillTheTypedParen() {
    assertEquals('(', typedCharBetween("os.path.join", "os.path.join()", caret = 13))
  }

  /** Text that did not change was a caret move, not a keystroke - `null`, not a guess. */
  @Test
  fun aCaretMove_typesNothing() {
    assertNull(typedCharBetween("join(path,", "join(path,", caret = 4))
  }

  /** A deletion removed rather than typed - no trigger char to report. */
  @Test
  fun aDeletion_typesNothing() {
    assertNull(typedCharBetween("join(path,", "join(path", caret = 9))
  }

  /**
   * `parameterHintsModel` reads the char the keystroke put where the caret is, so an edit
   * that lands before the caret's own region is not the keystroke it might look like.
   */
  @Test
  fun anInsertionElsewhere_typesNothingAtTheCaret() {
    assertNull(typedCharBetween("join()", "x = join()", caret = 6))
  }

  /** Replacing a selection with a trigger char still counts as having typed it. */
  @Test
  fun aTypedCharOverASelection_isStillTyped() {
    assertEquals('(', typedCharBetween("join(px)", "join(p()", caret = 7))
  }

  /** The wire's `[start, end]` pair is used verbatim - `getParameterLabelOffsets`' own rule. */
  @Test
  fun aRangedLabel_slicesTheSignatureVerbatim() {
    val signature = LanguageSignature(
      label = "join(path, *paths)",
      parameters = listOf(LanguageParameter(label = "path", labelRange = 5 to 9)),
    )

    assertEquals(5 to 9, parameterLabelOffsets(signature, 0))
  }

  /**
   * A string label is found in the signature's own label on a word boundary - the regex
   * `(\W|^)label(?=\W|$)` finds `paths` inside `*paths` because `*` is a non-word char.
   */
  @Test
  fun aStringLabel_isFoundOnAWordBoundary() {
    val signature = LanguageSignature(
      label = "join(path: LiteralString, /, *paths: LiteralString) -> LiteralString",
      parameters = listOf(
        LanguageParameter("path"),
        LanguageParameter("paths"),
      ),
    )

    assertEquals(5 to 9, parameterLabelOffsets(signature, 0))
    assertEquals(30 to 35, parameterLabelOffsets(signature, 1))
  }

  /** A label the signature does not contain highlights nothing - `[0, 0]` is no span. */
  @Test
  fun aLabelTheSignatureLacks_highlightsNothing() {
    val signature = LanguageSignature(
      label = "join(path)",
      parameters = listOf(LanguageParameter("mystery")),
    )

    assertNull(parameterLabelOffsets(signature, 0))
    assertNull(parameterLabelOffsets(signature, 3))
  }

  /** An empty string label is `[0, 0]` on the wire's own terms - nothing to light. */
  @Test
  fun anEmptyLabel_highlightsNothing() {
    val signature = LanguageSignature(
      label = "join()",
      parameters = listOf(LanguageParameter("")),
    )

    assertNull(parameterLabelOffsets(signature, 0))
  }
}
