package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The policy the page's reported height goes through before it becomes a cell's size.
 *
 * The page already debounces and thresholds its own reporting; this is the Kotlin side's
 * share - a floor so an empty cell is still tappable, and a ceiling so a runaway page cannot
 * ask for an unbounded view. The number is in dp, because CSS pixels already are.
 */
class EditorHeightTest {
  @Test
  fun accept_usesTheHeightThePageReported() {
    assertEquals(240, EditorHeight.accept(240))
  }

  /** An empty cell reports almost nothing, and a 4 px tall editor cannot be tapped into. */
  @Test
  fun accept_neverGoesBelowATappableMinimum() {
    assertEquals(EditorHeight.MINIMUM_DP, EditorHeight.accept(1))
    assertEquals(EditorHeight.MINIMUM_DP, EditorHeight.accept(0))
  }

  /** A page in a layout loop must not be able to ask for a view taller than the world. */
  @Test
  fun accept_clampsAHeightThatRunsAway() {
    assertEquals(EditorHeight.MAXIMUM_DP, EditorHeight.accept(1_000_000))
  }

  /** A negative height is not a small cell, it is a broken message. */
  @Test
  fun accept_treatsNonsenseAsTheMinimum() {
    assertEquals(EditorHeight.MINIMUM_DP, EditorHeight.accept(-40))
  }
}
