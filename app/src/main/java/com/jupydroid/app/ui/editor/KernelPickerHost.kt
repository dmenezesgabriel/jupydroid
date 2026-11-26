package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * The `selectKernel` dialog and the condition that raises it - lifted out of [EditorScreen]
 * because the branchcount the pair adds there is a complexity gate's worth on its own.
 *
 * `Context.initialize` -> `selectKernel` (`docregistry/src/context.ts:599-603`): a kernelspec the
 * file names and the catalogue has never heard of opens the picker as soon as the document is
 * ready, and again for each refused run (`cellexecutor.ts:69`). The owe
 * [EditorUiState.kernelPickRequired] counts is the key rather than the groups: a second owe while
 * the first is still up must still be a change. The dialog draws while its groups are in state -
 * see [KernelPickerDialog] for the reference's shape.
 */
@Composable
internal fun KernelPickerHost(state: EditorUiState, kernel: NotebookKernel) {
  LaunchedEffect(state.kernelPickRequired) {
    if (state.kernelPickRequired > 0) kernel.picker.open()
  }
  state.kernelPicker?.let { groups ->
    KernelPickerDialog(
      groups = groups,
      notebookTitle = state.title,
      noKernel = !state.kernelConnected,
      kernelDisplayName = state.kernelDisplayName,
      onPick = kernel.picker::pick,
      onDismiss = kernel.picker::dismiss,
    )
  }
}
