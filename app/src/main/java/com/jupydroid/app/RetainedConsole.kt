package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.ui.editor.NotebookKernelSession
import java.util.concurrent.atomic.AtomicInteger

/**
 * What an open Console needs from beside the back stack, kept for as long as the Console it serves.
 *
 * The `Console` key is saved with the stack and its `ConsoleViewModel` is kept in the entry's store,
 * so a relaunched activity - a rotation relaunches it, and so does a dark-mode switch - still has the
 * tab. Held by plain `remember` the session came back null beside it and the entry composed nothing,
 * and the count restarted so the next card was "Console 1" again (`ConsoleRecreationTest`). A
 * `ViewModel` of the activity for [RetainedOpenDocuments]' reason.
 */
class RetainedConsole : ViewModel() {
  /**
   * The session `NotebookKernel.attachConsole()` or the Launcher's card minted for the Console last
   * opened - a live object two `NavEntry`s with separate `ViewModelStore`s share, so it travels
   * here rather than in the key. One at a time: a second standalone card sweeps the first.
   */
  var session by mutableStateOf<NotebookKernelSession?>(null)

  // `Private.count` (`packages/console/src/panel.ts:332`) - how many consoles have been minted, so
  // the second card is "Console 2". Here rather than in the Launcher entry: a `viewModel {}` there is
  // per-entry, and each launcher push would restart the numbering at "Console 1".
  private val ordinal = AtomicInteger(0)

  /**
   * The number the next minted Console is titled with: 1, then 2, and so on.
   *
   * ```
   * LauncherViewModel(…, consoleOrdinal = retainedConsole::nextOrdinal)
   * ```
   */
  fun nextOrdinal(): Int = ordinal.incrementAndGet()
}

/**
 * The Console holders the activity already keeps, or new empty ones on its first composition.
 *
 * ```
 * val retainedConsole = rememberRetainedConsole()
 * retainedConsole.session?.let { session -> ConsoleScreen(…) }
 * ```
 */
@Composable
fun rememberRetainedConsole(): RetainedConsole = viewModel { RetainedConsole() }
