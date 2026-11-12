package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.KernelPickGroup
import com.jupydroid.app.domain.model.KernelPickOption
import com.jupydroid.app.domain.model.initialPick
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.theme.Spacing
import com.jupydroid.extension.api.FocusRingTextButton

/**
 * `SessionContextDialogs.selectKernel` (`packages/apputils/src/sessioncontext.tsx:1398-1460) -
 * the dialog a kernel is picked from.
 *
 * The reference's body is a `<select>` whose `<optgroup>`s are the groups [kernelPickerGroups]
 * built - the phone's analogue is a radio list under the same group labels, inside the same
 * *Select Kernel* dialog with *Select* and a Cancel that reads `kernelDisplayName` ("No
 * Kernel") when nothing is bound (`:1407-1410`). The marked row is the `<select>`'s
 * `selected` option; the first row stands in when nothing is marked, the browser's own default.
 *
 * ```
 * state.kernelPicker?.let { KernelPickerDialog(groups = it, notebookTitle = state.title, …) }
 * ```
 */
@Composable
internal fun KernelPickerDialog(
  groups: List<KernelPickGroup>,
  /** `sessionContext.name` - the file the session answers for, in the dialog's label line. */
  notebookTitle: String,
  /** `sessionContext.hasNoKernel` - relabels the cancel button with the kernel name shown. */
  noKernel: Boolean,
  kernelDisplayName: String,
  onPick: (KernelChoice?) -> Unit,
  onDismiss: () -> Unit,
) {
  // The `<select>`'s untouched answer: the marked row, or the first - the browser picks one
  // either way, so "Select" on a dialog nobody tapped still has an answer.
  var picked by remember(groups) { mutableStateOf(groups.initialPick()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select Kernel") },
    text = {
      // The whole body is one lazy list, label included. The cap matters, not just laziness:
      // a list whose content fits its own measure reports no scroll range, and items landing
      // past the dialog's text-slot clip were rendered yet invisible to accessibility and
      // unreachable by drag (kernel-picker-tablet tablet run). Bounded below the slot, the list
      // scrolls for real and every row reaches the viewport.
      LazyColumn(modifier = Modifier.heightIn(max = DIALOG_LIST_MAX).testTag(KERNEL_PICKER_LIST_TAG)) {
        // `Select kernel for: "<name>"` - the label above the `<select>` (`:1873`).
        item {
          Text(
            "Select kernel for: \"$notebookTitle\"",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.sm),
          )
        }
        groups.forEach { group ->
          item {
            Text(
              group.label,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.outline,
              modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
            )
          }
          group.options.forEach { option ->
            item {
              KernelPickRow(option = option, picked = option.choice == picked, onClick = { picked = option.choice })
            }
          }
        }
      }
    },
    confirmButton = { FocusRingTextButton(onClick = { onPick(picked) }) { Text("Select") } },
    dismissButton = {
      FocusRingTextButton(onClick = onDismiss) { Text(if (noKernel) kernelDisplayName else "Cancel") }
    },
  )
}

/**
 * One radio row of the `<select>`: `selectable` so the whole row is the tap target, merged so
 * `assertIsSelected` and a Maestro tap land on a node named by the option's text.
 *
 * `heightIn(min = 48.dp)` is the fix for a real defect, not decoration: `selectable` pads a
 * shorter row's *semantics* bounds to the 48dp touch minimum without growing its layout, and
 * for the last row that padded tail crossed the list's clip - the row drew fully yet was
 * flagged invisible, unreachable to a11y and Maestro (kernel-picker-tablet tablet run). A row
 * whose real height already meets the touch target has nothing to inflate.
 */
@Composable
internal fun KernelPickRow(option: KernelPickOption, picked: Boolean, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = ROW_TOUCH_MIN)
      .selectable(selected = picked, role = Role.RadioButton, onClick = onClick)
      .semantics(mergeDescendants = true) {},
  ) {
    RadioButton(selected = picked, onClick = null)
    Text(option.text, style = MaterialTheme.typography.bodyLarge)
  }
}

/** Tag on the options list - `performScrollToNode` reaches a row past the viewport through it. */
internal const val KERNEL_PICKER_LIST_TAG = "kernelPickerList"

/** The platform's minimum touch target; a row's *layout* must meet it, not just its semantics. */
private val ROW_TOUCH_MIN = 48.dp

/** Below the dialog's text-slot height (~414px on the tablet) so the list always gets a scroll range. */
private val DIALOG_LIST_MAX = 260.dp
