package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import com.jupydroid.app.domain.port.CompletionMatch
import com.jupydroid.app.domain.port.CompletionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyCompletionTest {
  @Test
  fun replacesTheRangeTheKernelNamedAndLeavesTheCursorAfterIt() {
    val state = TextFieldState("pri", TextRange(3))

    applyCompletion(state, completions("pri", 3, matched = 0 to 3), CompletionMatch("print", "print"))

    assertEquals("print", state.text.toString())
    assertEquals(TextRange(5), state.selection)
  }

  /** A kernel answers `np.ar` with whole names; the range is what stops it becoming np.np.arange. */
  @Test
  fun replacesOnlyWhatTheRangeCoversRatherThanTheWholeLine() {
    val state = TextFieldState("x = np.ar", TextRange(9))

    applyCompletion(state, completions("x = np.ar", 9, matched = 4 to 9), CompletionMatch("np.arange", "np.arange"))

    assertEquals("x = np.arange", state.text.toString())
  }

  /**
   * P5e-2: the label is what the user *reads*, not what accepting writes - jedi signature-
   * resolves labels (`join(path, paths)`), so splicing the label would type the parameter list.
   * The insert text is what goes in, which is also what keeps `insertTextFormat:2` payload
   * strings out of the document while `snippetSupport` stays undeclared.
   */
  @Test
  fun splicesTheInsertTextNotTheSignatureBearingLabel() {
    val state = TextFieldState("os.path.jo", TextRange(10))
    val match = CompletionMatch(insert = "join", label = "join(path, paths)", kind = 3)

    applyCompletion(state, completions("os.path.jo", 10, matched = 8 to 10, match = match), match)

    assertEquals("os.path.join", state.text.toString())
    assertEquals(TextRange(12), state.selection)
  }

  /**
   * The range is an offset into the code that was asked about. A reply arriving after the user
   * typed on would otherwise edit characters nobody pointed at.
   */
  @Test
  fun refusesWhenTheCodeMovedOnSinceTheRequest() {
    val state = TextFieldState("printed", TextRange(7))

    applyCompletion(state, completions("pri", 3, matched = 0 to 3), CompletionMatch("print", "print"))

    assertEquals("printed", state.text.toString())
  }

  @Test
  fun refusesARangeThatDoesNotFitTheCode() {
    val state = TextFieldState("pri", TextRange(3))

    applyCompletion(state, completions("pri", 3, matched = 0 to 99), CompletionMatch("print", "print"))

    assertEquals("pri", state.text.toString())
  }

  private fun completions(
    code: String,
    cursor: Int,
    matched: Pair<Int, Int>,
    match: CompletionMatch = CompletionMatch("print", "print"),
  ) = CellCompletions(
    query = CompletionQuery(code, cursor),
    result = CompletionResult(listOf(match), matched.first, matched.second),
  )
}
