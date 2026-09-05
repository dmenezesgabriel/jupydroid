package com.jupydroid.app.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `docs/PLAN.md`'s P4j-3a: Termux's own `SpecialButtonState{isActive,isLocked}`
 * (`extrakeys/SpecialButtonState.java`), transcribed as an immutable value - see
 * [TerminalModifierLatch]'s own KDoc for the three transitions this pins.
 */
class TerminalModifierLatchTest {
  @Test
  fun aTapArmsAnInactiveLatch() {
    val latch = TerminalModifierLatch().tapped()

    assertEquals(TerminalModifierLatch(isActive = true, isLocked = false), latch)
  }

  @Test
  fun aSecondTapDisarmsAnActiveUnlockedLatch() {
    val latch = TerminalModifierLatch(isActive = true).tapped()

    assertEquals(TerminalModifierLatch(isActive = false, isLocked = false), latch)
  }

  @Test
  fun aTapOnALockedLatchUnlocksAndDeactivatesInOneStep() {
    val latch = TerminalModifierLatch(isActive = true, isLocked = true).tapped()

    assertEquals(TerminalModifierLatch(isActive = false, isLocked = false), latch)
  }

  @Test
  fun aLongPressOnAnInactiveLatchLocksItActive() {
    val latch = TerminalModifierLatch().longPressed()

    assertEquals(TerminalModifierLatch(isActive = true, isLocked = true), latch)
  }

  @Test
  fun aLongPressOnAnActiveLockedLatchUnlocksAndDeactivates() {
    val latch = TerminalModifierLatch(isActive = true, isLocked = true).longPressed()

    assertEquals(TerminalModifierLatch(isActive = false, isLocked = false), latch)
  }

  @Test
  fun consumingAnInactiveLatchReportsFalseAndLeavesItUntouched() {
    val (wasActive, after) = TerminalModifierLatch().consume()

    assertEquals(false, wasActive)
    assertEquals(TerminalModifierLatch(), after)
  }

  @Test
  fun consumingAnActiveUnlockedLatchReportsTrueAndClearsIt() {
    val (wasActive, after) = TerminalModifierLatch(isActive = true).consume()

    assertEquals(true, wasActive)
    assertEquals(TerminalModifierLatch(isActive = false, isLocked = false), after)
  }

  @Test
  fun consumingALockedLatchReportsTrueAndLeavesItArmed() {
    val (wasActive, after) = TerminalModifierLatch(isActive = true, isLocked = true).consume()

    assertEquals(true, wasActive)
    assertEquals(TerminalModifierLatch(isActive = true, isLocked = true), after)
  }
}
