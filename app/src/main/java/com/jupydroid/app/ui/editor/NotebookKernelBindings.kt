package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.LanguageFormatOptions

/**
 * The document-half answers a kernel needs from the screen that owns it right now.
 *
 * A run that outlives its screen keeps the kernel, so these cannot be the cleared screen's
 * captures: adoption swaps this object when a reopening editor takes the run over, which is what
 * makes the run's next checkpoint, kernel-bound write, close policy and format ask belong to the
 * document on screen rather than to the `ViewModel` that is gone.
 */
internal class NotebookKernelBindings(
  /** What a finished run does to the document - write it, or mark it owed. */
  val checkpoint: suspend () -> Unit,
  /** `kernelChanged` -> `metadata.kernelspec`, told each time a kernel binds. */
  val onKernelBound: suspend (KernelSession, KernelSpec?) -> Unit,
  /** `kernelShutdown`, read at close rather than at open - see [NotebookKernel.close]. */
  val shutdownOnClose: () -> Boolean,
  /** The `options` a range-format carries, read at ask time. */
  val formattingOptions: () -> LanguageFormatOptions,
)

/**
 * Rebinds [NotebookKernel]'s document-half answers to the screen that took its run over. The
 * session, runs and collectors are untouched: only what the next checkpoint, kernel-bound write,
 * close or format ask *means* changes hands.
 */
internal fun NotebookKernel.adopt(bindings: NotebookKernelBindings) {
  currentBindings = bindings
}
