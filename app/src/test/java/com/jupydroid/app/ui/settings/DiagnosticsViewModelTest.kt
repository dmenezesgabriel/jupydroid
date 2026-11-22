package com.jupydroid.app.ui.settings

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeErrorLog
import com.jupydroid.app.data.fake.FakeErrorLogSharer
import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import com.jupydroid.app.domain.model.ErrorLogSummary
import com.jupydroid.app.domain.port.ErrorLog
import com.jupydroid.app.domain.port.ErrorLogSharer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** An [ErrorLog] whose storage refuses every read and delete, as a full or unmounted card does. */
private class UnreadableErrorLog : ErrorLog {
  override suspend fun append(entry: ErrorLogEntry) = Unit

  override suspend fun prune() = Unit

  override suspend fun summary(): ErrorLogSummary = throw IOException("/sdcard/Android/data: Permission denied")

  override suspend fun clear() = throw IOException("/sdcard/Android/data: Permission denied")
}

/** The Diagnostics section's state: what the log holds, a clear awaiting confirmation, and a read that failed. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
  @get:Rule val dispatcher = MainDispatcherRule()

  private suspend fun logHoldingTwoEntries() = FakeErrorLog().also { log ->
    log.append(ErrorLogEntry(timestampMillis = 1, kind = ErrorLogKind.CRASH))
    log.append(ErrorLogEntry(timestampMillis = 2, kind = ErrorLogKind.EXIT))
  }

  private fun loaded(viewModel: DiagnosticsViewModel) = viewModel.uiState.value as DiagnosticsUiState.Loaded

  @Test
  fun showsWhatTheLogHolds() = runTest {
    val log = logHoldingTwoEntries()

    val state = loaded(DiagnosticsViewModel(log, FakeErrorLogSharer()))

    assertEquals(log.summary(), state.summary)
    assertEquals(false, state.confirmingClear)
  }

  @Test
  fun askingToClearWaitsForConfirmationAndClearsNothing() = runTest {
    val log = logHoldingTwoEntries()
    val viewModel = DiagnosticsViewModel(log, FakeErrorLogSharer())

    viewModel.askToClear()

    assertEquals(true, loaded(viewModel).confirmingClear)
    assertEquals(2, log.entries.size)
  }

  @Test
  fun dismissingTheConfirmationKeepsTheEntries() = runTest {
    val log = logHoldingTwoEntries()
    val viewModel = DiagnosticsViewModel(log, FakeErrorLogSharer())
    viewModel.askToClear()

    viewModel.dismissClear()

    assertEquals(false, loaded(viewModel).confirmingClear)
    assertEquals(2, log.entries.size)
  }

  @Test
  fun confirmingClearsTheLogAndShowsNothingLeft() = runTest {
    val log = logHoldingTwoEntries()
    val viewModel = DiagnosticsViewModel(log, FakeErrorLogSharer())
    viewModel.askToClear()

    viewModel.confirmClear()

    assertEquals(emptyList<ErrorLogEntry>(), log.entries)
    assertEquals(ErrorLogSummary(entryCount = 0, sizeBytes = 0), loaded(viewModel).summary)
    assertEquals(false, loaded(viewModel).confirmingClear)
  }

  @Test
  fun confirmingWithoutHavingAskedClearsNothing() = runTest {
    val log = logHoldingTwoEntries()
    val viewModel = DiagnosticsViewModel(log, FakeErrorLogSharer())

    viewModel.confirmClear()

    assertEquals(2, log.entries.size)
  }

  @Test
  fun aLogThatCannotBeReadIsStatedWithItsCause() = runTest {
    val state = DiagnosticsViewModel(UnreadableErrorLog(), FakeErrorLogSharer()).uiState.value

    assertTrue("$state", state is DiagnosticsUiState.Failed)
    assertTrue(state.toString(), (state as DiagnosticsUiState.Failed).message.contains("Permission denied"))
  }

  @Test
  fun aClearThatFailsIsStatedWithItsCause() = runTest {
    val viewModel = DiagnosticsViewModel(FailsToClearErrorLog(), FakeErrorLogSharer())
    viewModel.askToClear()

    viewModel.confirmClear()

    val state = viewModel.uiState.value
    assertTrue(state.toString(), state is DiagnosticsUiState.Failed)
    assertTrue(state.toString(), (state as DiagnosticsUiState.Failed).message.contains("Read-only file system"))
  }

  @Test
  fun sharingHandsTheLogToTheSharerOnce() = runTest {
    val sharer = FakeErrorLogSharer()
    val viewModel = DiagnosticsViewModel(logHoldingTwoEntries(), sharer)

    viewModel.share()

    assertEquals(1, sharer.shareCount)
    assertEquals(2, loaded(viewModel).summary.entryCount)
  }

  @Test
  fun sharingAnEmptyLogSharesNothing() = runTest {
    val sharer = FakeErrorLogSharer()

    DiagnosticsViewModel(FakeErrorLog(), sharer).share()

    assertEquals(0, sharer.shareCount)
  }

  @Test
  fun aShareThatFailsIsStatedWithItsCause() = runTest {
    val viewModel = DiagnosticsViewModel(logHoldingTwoEntries(), FailsToShareErrorLogSharer())

    viewModel.share()

    val state = viewModel.uiState.value
    assertTrue(state.toString(), state is DiagnosticsUiState.Failed)
    assertTrue(state.toString(), (state as DiagnosticsUiState.Failed).message.contains("No space left on device"))
  }
}

/** A cache that cannot hold the bundle - the disk the share copies into is full. */
private class FailsToShareErrorLogSharer : ErrorLogSharer {
  override suspend fun share() = throw IOException("No space left on device")
}

/** Reads fine, refuses to delete - a volume remounted read-only under a running app. */
private class FailsToClearErrorLog : ErrorLog {
  override suspend fun append(entry: ErrorLogEntry) = Unit

  override suspend fun prune() = Unit

  override suspend fun summary() = ErrorLogSummary(entryCount = 1, sizeBytes = 100)

  override suspend fun clear() = throw IOException("Read-only file system")
}
