package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.MarginedAlertDialog

/**
 * *"Replace 5 occurrences across 2 files with 'dog'?"* - asked before a single file is written.
 *
 * `dialogService.confirm` with `replaceAll.confirmation.title`, the counted message and
 * `replaceAll.confirm.button` (`searchView.ts:830-836`). The same `AlertDialog` shape as
 * `DeleteFileDialog` (`:extension-api`), which is this app's one idiom for "ask before doing something that
 * cannot be shrugged off".
 *
 * **The reference's strings verbatim, where the button that opens this was named apart.** A modal
 * dialog is a tree of its own: nothing of the notebook's find bar is in it, so *"Replace All"* and
 * *"Replace"* collide with nothing here - see [REPLACE_ALL_IN_WORKSPACE] for the button, which is
 * in the same tree as the find bar's and therefore is not verbatim.
 *
 * The primary button is **not** drawn in `error`, where a delete's is: this is reversible for
 * exactly as long as its report is on the panel ([SearchReplaceReport]), and a delete is not.
 *
 * ```
 * state.confirming?.let { ReplaceAllDialog(it, onReplace = viewModel.replaceAll::confirmed, …) }
 * ```
 */
@Composable
fun ReplaceAllDialog(asked: ReplaceAllRequest, onReplace: () -> Unit, onDismiss: () -> Unit) {
  MarginedAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(REPLACE_ALL_TITLE) },
    text = { Text(replaceAllConfirmation(asked.occurrences, asked.fileCount, asked.replaceValue)) },
    confirmButton = { FocusRingTextButton(onClick = onReplace) { Text(REPLACE_ALL_CONFIRM) } },
    dismissButton = { FocusRingTextButton(onClick = onDismiss) { Text(REPLACE_ALL_CANCEL) } },
  )
}

/** `replaceAll.confirmation.title` (`searchView.ts:831`). */
internal const val REPLACE_ALL_TITLE = "Replace All"

/** `replaceAll.confirm.button` (`searchView.ts:833`), without the `&&` that marks its mnemonic. */
internal const val REPLACE_ALL_CONFIRM = "Replace"

/** `IConfirmation`'s own cancel, which the reference never states because it is the default. */
internal const val REPLACE_ALL_CANCEL = "Cancel"
