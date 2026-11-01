@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.ActiveCellRuns
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A run is in flight from the moment it is asked for, and the kernel starting is part of it.
 *
 * **This is the regression test for a measured device failure**
 * (the run of 2026-08-14). The backstop read a count that only
 * rose once the execute flow was being collected - which is *after* the Space is installed, the
 * server started and the kernel started. The tablet's server log has the arithmetic: the cell's
 * `execute_request` went out at 01:22:49, fourteen seconds after the app was backgrounded, and the
 * stopper's thirty-second grace had already been counting for all of them. It killed the cell it
 * exists to protect.
 */
class RunInFlightTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aRunCountsWhileTheKernelIsStillStarting() = runTest {
    val runs = ActiveCellRuns()
    val gateway = SlowToConnectKernelGateway()
    val editor = loadedEditor(gateway = gateway, activeCellRuns = runs)

    editor.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals("the kernel is still starting, and the run is what asked for it", 1, runs.inFlight.value)

    gateway.letItConnect()
    advanceUntilIdle()
    assertEquals("the cell has finished", 0, runs.inFlight.value)
  }

  @Test
  fun runningEveryCellCountsUntilTheLastOneIsDone() = runTest {
    val runs = ActiveCellRuns()
    val gateway = SlowToConnectKernelGateway()
    val editor = loadedEditor(
      gateway = gateway,
      cells = listOf(codeCell, secondCodeCell),
      activeCellRuns = runs,
    )

    editor.kernel.run(RunRange.ALL)
    advanceUntilIdle()
    assertEquals(1, runs.inFlight.value)

    gateway.letItConnect()
    advanceUntilIdle()
    assertEquals(0, runs.inFlight.value)
  }

  @Test
  fun anEditorThatRanNothingHasNothingInFlight() = runTest {
    val runs = ActiveCellRuns()

    loadedEditor(activeCellRuns = runs)
    advanceUntilIdle()

    assertEquals(0, runs.inFlight.value)
  }
}
