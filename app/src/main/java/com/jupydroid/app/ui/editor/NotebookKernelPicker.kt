package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.kernelPickerGroups
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelPreference
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The `selectKernel` dialog's kernel half (`packages/apputils/src/sessioncontext.tsx:1398-1460`):
 * open it, answer it, dismiss it.
 *
 * A collaborator of [NotebookKernel]'s like [NotebookCellRuns] and [NotebookAsks], and split for
 * the same reason - the picker's groups, its three answers and the changing-flag around the
 * PATCH are one responsibility; the kernel's run/restart/end acts are another, and the fourteen
 * functions of the two together tripped `TooManyFunctions` before either admitted it.
 *
 * It holds the [NotebookKernelSession] directly because everything it asks - the preference, the
 * bound kernel's id, the `changeKernel` PATCH - is the session's to answer; the one act that is
 * not is ending it, which [onShutdown] delegates back for.
 */
class NotebookKernelPicker(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val session: NotebookKernelSession,
  /** The entered Space, whose catalogue the dialog lists. */
  private val space: Space,
  private val kernelSpecs: KernelSpecs,
  private val kernelSessions: KernelSessions,
  private val scope: CoroutineScope,
  /** The *Select* on the "No Kernel" row - `shutdown()` on the owning kernel. */
  private val onShutdown: () -> Unit,
) {
  /**
   * `selectKernel`'s open (`:1398`): the dialog's groups are fetched when it is asked for, not
   * kept - the catalogue and the running list are the server's answers and both are passive
   * reads, so a stopped server's picker still lists the Space's own specs.
   */
  fun open() {
    scope.launch {
      val groups = kernelPickerGroups(
        specs = kernelSpecs.specs(space),
        running = kernelSessions.running(),
        preference = session.kernelPreference ?: KernelPreference(),
        kernelId = session.opened?.kernelId,
        kernelDisplayName = if (session.opened != null) uiState.value.kernelDisplayName else null,
      )
      uiState.update { it.copy(kernelPicker = groups) }
    }
  }

  /**
   * The picker's *Select* (`:1447-1458`): **"No Kernel" is the pick that ends the session** -
   * `model === null && !hasNoKernel -> shutdown()` - and any other choice goes to
   * `SessionContext.changeKernel`, which patches the session rather than ending it
   * (`sessioncontext.tsx:956-960`) and starts one when there is none.
   *
   * `_isChangingKernel` is carried for the length of the PATCH, so the indicator says a kernel
   * is coming up (`'starting'`, `:623-625`) rather than still showing the one being replaced.
   */
  fun pick(choice: KernelChoice?) {
    uiState.update { it.copy(kernelPicker = null) }
    if (choice == null) {
      onShutdown()
      return
    }
    scope.launch {
      uiState.update { it.copy(kernelChanging = true) }
      try {
        session.changeKernel(choice)
      } finally {
        uiState.update { it.copy(kernelChanging = false) }
      }
    }
  }

  /** The picker's *Cancel* - the reference's early `return`: nothing is asked of the kernel. */
  fun dismiss() {
    uiState.update { it.copy(kernelPicker = null) }
  }
}
