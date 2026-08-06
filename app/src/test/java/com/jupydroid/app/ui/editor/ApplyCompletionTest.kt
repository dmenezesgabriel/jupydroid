package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import com.jupydroid.app.domain.port.CompletionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyCompletionTest {
  @Test
  fun replacesTheRangeTheKernelNamedAndLeavesTheCursorAfterIt() {
    val state = TextFieldState("pri", TextRange(3))

    applyCompletion(state, completions("pri", 3, matched = 0 to 3), "print")

    assertEquals("print", state.text.toString())
    assertEquals(TextRange(5), state.selection)
  }

  /** A kernel answers `np.ar` with whole names; the range is what stops it becoming np.np.arange. */
  @Test
  fun replacesOnlyWhatTheRangeCoversRatherThanTheWholeLine() {
    val state = TextFieldState("x = np.ar", TextRange(9))

    applyCompletion(state, completions("x = np.ar", 9, matched = 4 to 9), "np.arange")

    assertEquals("x = np.arange", state.text.toString())
  }

  /**
   * The range is an offset into the code that was asked about. A reply arriving after the user
   * typed on would otherwise edit characters nobody pointed at.
   */
  @Test
  fun refusesWhenTheCodeMovedOnSinceTheRequest() {
    val state = TextFieldState("printed", TextRange(7))

    applyCompletion(state, completions("pri", 3, matched = 0 to 3), "print")

    assertEquals("printed", state.text.toString())
  }

  @Test
  fun refusesARangeThatDoesNotFitTheCode() {
    val state = TextFieldState("pri", TextRange(3))

    applyCompletion(state, completions("pri", 3, matched = 0 to 99), "print")

    assertEquals("pri", state.text.toString())
  }

  private fun completions(code: String, cursor: Int, matched: Pair<Int, Int>) = CellCompletions(
    query = CompletionQuery(code, cursor),
    result = CompletionResult(listOf("print"), matched.first, matched.second),
  )
}
