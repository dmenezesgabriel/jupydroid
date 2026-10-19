package com.jupydroid.app.ui.terminal

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * gesture scrollback: [TerminalPaneViewModel.scrollBy]'s clamped,
 * remainder-carrying row arithmetic (3d-3), the reset-to-live-on-new-output rule Termux's own
 * `TerminalView.onScreenUpdated` takes by default (3d-3), and the alternate screen's own redirect
 * to arrow keystrokes instead of a scroll (3d-4). Split out of `TerminalPaneViewModelTest.kt` once
 * that file crossed the 500-line file-length gate - `TerminalPaneViewModelFixture.kt` is what both
 * files share.
 */
class TerminalPaneViewModelScrollbackTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * sub-cell drag distance is not lost between calls - Termux's own
   * `mScrollRemainder`. Two 4px drags over a 10px cell move nothing on their own; a third pushes
   * the accumulated 12px total past one whole cell, landing exactly one row.
   */
  @Test
  fun scrollByRemembersTheRemainderAcrossCalls() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    advanceUntilIdle()
    scrollDownOneLine(viewModel) // one row of scrollback to move into

    viewModel.scrollBy(pixelDeltaY = -4f, cellHeightPx = 10)
    viewModel.scrollBy(pixelDeltaY = -4f, cellHeightPx = 10)
    assertEquals("8px of 10 is not yet a whole row", 0, viewModel.uiState.value.topRow)

    viewModel.scrollBy(pixelDeltaY = -4f, cellHeightPx = 10)
    assertEquals(-1, viewModel.uiState.value.topRow)
  }

  @Test
  fun scrollByClampsAtTheLiveScreen() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    advanceUntilIdle()

    val consumed = viewModel.scrollBy(pixelDeltaY = 1000f, cellHeightPx = 10)

    assertEquals(0, viewModel.uiState.value.topRow)
    assertEquals(0f, consumed, 0f)
  }

  @Test
  fun scrollByClampsAtTheOldestScrollbackRowAndReportsOnlyWhatWasActuallyConsumed() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    advanceUntilIdle()
    scrollDownOneLine(viewModel) // activeTranscriptRows == 1

    // Ask for far more than the one row of history this screen actually has.
    val consumed = viewModel.scrollBy(pixelDeltaY = -1000f, cellHeightPx = 10)

    assertEquals(-1, viewModel.uiState.value.topRow)
    assertEquals(
      "a fling's own deceleration must stop where the grid actually stopped, not where it asked to go",
      -10f,
      consumed,
      0f,
    )
  }

  @Test
  fun scrollByBumpsRedrawWhenTopRowActuallyMoves() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    advanceUntilIdle()
    scrollDownOneLine(viewModel)
    val before = viewModel.uiState.value.redraw

    viewModel.scrollBy(pixelDeltaY = -10f, cellHeightPx = 10)

    assertEquals(before + 1, viewModel.uiState.value.redraw)
  }

  /**
   * Termux's own `TerminalView.onScreenUpdated` default path: no text selection, no auto-scroll
   * lock exist in this app to carve an exception out for, so any new output while scrolled back
   * snaps straight back to the live screen - a named simplification of the reference, not a
   * silent omission.
   */
  @Test
  fun newOutputWhileScrolledBackSnapsBackToTheLiveScreen() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    attachOn(viewModel)
    advanceUntilIdle()
    gateway.push(TerminalEvent.Ready)
    scrollDownOneLine(viewModel)
    viewModel.scrollBy(pixelDeltaY = -10f, cellHeightPx = 10)
    advanceUntilIdle()
    assertEquals(-1, viewModel.uiState.value.topRow)

    gateway.push(TerminalEvent.Output("x"))
    advanceUntilIdle()

    assertEquals(0, viewModel.uiState.value.topRow)
  }

  /**
   * `TerminalView.onScreenUpdated`'s carve-out (termux-app `3df69d1d`): while a selection exists,
   * output that scrolls the screen moves the view up with the text instead of to the live screen,
   * so the selected word stays where the user is looking.
   */
  @Test
  fun outputWhileAWordIsSelectedKeepsTheWordInView() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    attachOn(viewModel)
    advanceUntilIdle()
    gateway.push(TerminalEvent.Output("abc\r\n"))
    advanceUntilIdle()
    viewModel.selector.selectWordAt(column = 0, row = 0)

    gateway.push(TerminalEvent.Output("1\r\n2\r\n"))
    advanceUntilIdle()

    assertEquals(-1, viewModel.uiState.value.topRow)
    assertEquals("abc", viewModel.selector.selectedText())
  }

  /**
   * the alternate screen (`vi`, `less`, `htop`) carries no scrollback
   * at all, so a drag there is Termux's own `doScroll`'s special case - redirected to Up/Down
   * arrow keystrokes instead of a `topRow` move. `ScreenBuffers.isAlternateLive` is already public
   * through `TerminalScreen.buffers` - no new delegate property needed, the plan row's own
   * assumption corrected once the actual visibility was checked.
   */
  @Test
  fun scrollByForwardsUpArrowsWhileTheAlternateScreenIsLive() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    advanceUntilIdle()
    enterAlternateScreen(viewModel)

    viewModel.scrollBy(pixelDeltaY = -10f, cellHeightPx = 10)
    advanceUntilIdle()

    assertEquals("a redirected drag must not move the viewport", 0, viewModel.uiState.value.topRow)
    assertEquals(listOf("$ESC[A"), gateway.sent)
  }

  @Test
  fun scrollByForwardsDownArrowsWhileTheAlternateScreenIsLive() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    advanceUntilIdle()
    enterAlternateScreen(viewModel)

    viewModel.scrollBy(pixelDeltaY = 10f, cellHeightPx = 10)
    advanceUntilIdle()

    assertEquals(listOf("$ESC[B"), gateway.sent)
  }

  @Test
  fun scrollBySendsOneArrowPressPerWholeRowOfDrag() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway, rows = 3, columns = 5)
    advanceUntilIdle()
    enterAlternateScreen(viewModel)

    viewModel.scrollBy(pixelDeltaY = -25f, cellHeightPx = 10) // two whole rows, 5px remainder
    advanceUntilIdle()

    assertEquals(listOf("$ESC[A", "$ESC[A"), gateway.sent)
  }
}
