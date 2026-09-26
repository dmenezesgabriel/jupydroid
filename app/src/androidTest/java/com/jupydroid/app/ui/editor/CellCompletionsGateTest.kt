package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.port.CompletionMatch
import com.jupydroid.app.domain.port.CompletionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `docs/PLAN.md`'s P6c-6: `enabled = false` has to stop [rememberCellCompletions] from ever
 * offering a completion, not merely compile - and the positive case beside it proves the same
 * harness *would* offer one, so the negative case cannot pass by accident
 * (`docs/PLAN.md`'s own P2z/P2x-3f finding: an assertion that cannot fail is a defect the reader
 * cannot see in a green log).
 */
@RunWith(AndroidJUnit4::class)
class CellCompletionsGateTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun disabledNeverOffersCompletionsEvenWhenTheKernelWould() {
    lateinit var completions: State<CellCompletions?>
    compose.setContent { completions = harness(enabled = false) }

    // Long enough that an answer would have arrived if one were coming: the pipeline debounces
    // by 250ms, so an immediate check here would pass whether or not the gate did anything.
    compose.mainClock.advanceTimeBy(SETTLE_GRACE_MS)
    compose.runOnIdle { assertNull(completions.value) }
  }

  @Test
  fun enabledOffersWhatTheKernelAnswers() {
    lateinit var completions: State<CellCompletions?>
    compose.setContent { completions = harness(enabled = true) }

    compose.waitUntil(timeoutMillis = REPORT_TIMEOUT_MS) { completions.value != null }
    assertEquals(MATCHES, completions.value?.result?.matches)
  }

  /** A field seeded with [QUERY] and a cursor at its end, so a query forms the moment it composes. */
  @Composable
  private fun harness(enabled: Boolean): State<CellCompletions?> {
    val state = rememberTextFieldState("")
    val focused = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
      state.edit {
        replace(0, length, QUERY)
        placeCursorBeforeCharAt(QUERY.length)
      }
    }
    return rememberCellCompletions(
      state = state,
      focused = focused,
      completionsAt = { _, _ -> CompletionResult(MATCHES, 0, QUERY.length) },
      enabled = enabled,
    )
  }

  private companion object {
    const val QUERY = "pri"
    val MATCHES = listOf(CompletionMatch("print", "print"))
    const val SETTLE_GRACE_MS = 600L
    const val REPORT_TIMEOUT_MS = 2_000L
  }
}
