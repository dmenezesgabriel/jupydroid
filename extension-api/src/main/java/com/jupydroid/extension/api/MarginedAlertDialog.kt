package com.jupydroid.extension.api

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * An alert dialog that keeps 24dp between its surface and the window's left and right edges, so a
 * dialog whose text wraps no longer fills a phone edge to edge.
 *
 * Compose's `Dialog` replaces the platform's 16dp-inset window background with a transparent one and
 * M3's `BasicAlertDialog` is `sizeIn(280dp, 560dp)` with nothing outside it, where Material
 * Components' own dialog insets 24dp (`mtrl_alert_dialog_background_inset_start`, 1.13.0). The
 * override hook that could do this for every dialog (`LocalBasicAlertDialogOverride`) is `internal`
 * in material3 1.4.0, so the margin is the padding outside `sizeIn`: it shrinks the constraints before
 * the 280 to 560dp clamp, and the window wraps surface plus margin, leaving a wide window as it was.
 *
 * It lives here, not in `:app`, because `DeleteFileDialog` and `RenameFileDialog` are drawn by this
 * module and cannot import the host (`ArchitectureGuardTest.uiLayer_callsNoMaterialAlertDialog`,
 * `FocusRingGuardTest.theContractNamesNoMaterialAlertDialog`). The 24dp is `Spacing.xl` in `:app`.
 *
 * ```
 * MarginedAlertDialog(
 *   onDismissRequest = onCancel,
 *   title = { Text("Save your work") },
 *   text = { Text("Save changes in \"$name\" before closing?") },
 *   confirmButton = { FocusRingTextButton(onClick = onSave) { Text("Save") } },
 * )
 * ```
 */
@Composable
public fun MarginedAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: @Composable (() -> Unit)? = null,
  title: @Composable (() -> Unit)? = null,
  text: @Composable (() -> Unit)? = null,
  properties: DialogProperties = DialogProperties(),
) {
  AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = confirmButton,
    modifier = modifier.padding(horizontal = SIDE_MARGIN),
    dismissButton = dismissButton,
    title = title,
    text = text,
    properties = properties,
  )
}

private val SIDE_MARGIN = 24.dp
