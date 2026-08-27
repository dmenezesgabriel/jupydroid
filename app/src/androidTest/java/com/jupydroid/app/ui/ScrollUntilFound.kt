package com.jupydroid.app.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performScrollToNode

/**
 * Scrolls [list] until [what] is composed, retrying while the walk that produces it is still running.
 *
 * **Scrolling is the assertion, not a workaround.** Since P2x-3e the search panel is one
 * `LazyColumn` - box, the `…`, the glob boxes, the messages and the tree - because with the glob
 * boxes open its fixed chrome is taller than the panel and everything below was composed, correct
 * and unreachable (`SearchResults`' own doc carries the reading). A row below the viewport of a lazy
 * list is not merely off screen: it does not exist in the semantics tree, so `onAllNodesWith…`
 * cannot wait for it.
 *
 * **A plain bounded loop rather than `waitUntil`, and that is a measurement.** A scroll *action*
 * inside a `waitUntil` condition nests one synchronisation inside another: run that way, the whole
 * instrumentation hung - `TestRunner` logged the test started and the app process spun at 100% with
 * no timeout ever firing. The action belongs outside the wait.
 *
 * ```
 * scrollUntilFound(compose, hasScrollAction(), hasText("print(cat)")) { "never scrolled to it" }
 * ```
 *
 * @param whenMissing what to say when it never arrives - the caller names the thing it wanted.
 */
internal fun scrollUntilFound(
  compose: ComposeContentTestRule,
  list: SemanticsMatcher,
  what: SemanticsMatcher,
  whenMissing: () -> String,
) {
  repeat(SCROLL_ATTEMPTS) {
    if (runCatching { compose.onNode(list).performScrollToNode(what) }.isSuccess) return
    Thread.sleep(SCROLL_POLL_MILLIS)
  }
  throw AssertionError(whenMissing())
}

/** Long enough for the 300 ms debounce plus a walk of an in-memory workspace, short enough to fail. */
private const val SCROLL_ATTEMPTS = 25

/** Between attempts, so a walk still in flight gets time rather than a tight spin. */
private const val SCROLL_POLL_MILLIS = 200L
