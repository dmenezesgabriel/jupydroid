package com.jupydroid.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jupydroid.app.domain.model.isValidNotebookName

const val RENAME_FIELD_TAG = "rename-field"

/**
 * Renames a notebook, from Recents or from the notebook itself.
 *
 * One dialog for both, because two would drift: JupyterLab keeps a single
 * `DocumentManagerDialogs.rename` and reaches it from the File menu and from the document title
 * alike (`packages/docmanager/src/dialogs.ts:55-100`).
 *
 * [onConfirm] is called **only when the title actually changed**. That is JupyterLab's rule -
 * `if (!newPath || newPath === fileName) return null`, silently, with no error - and it matters
 * more here than there: a write is what makes a notebook "updated", so confirming a dialog
 * without editing would push the notebook to the top of Recents for nothing. An unchanged or
 * blank title takes [onDismiss] instead, so the caller still closes the dialog either way.
 *
 * ```
 * if (renameOpen) {
 *   RenameNotebookDialog(
 *     currentTitle = notebook.title,
 *     onConfirm = { renameOpen = false; viewModel.renameNotebook(it) },
 *     onDismiss = { renameOpen = false },
 *   )
 * }
 * ```
 */
@Composable
fun RenameNotebookDialog(
  currentTitle: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  // Selected, not merely filled: typing should replace the name and leave `.py` alone.
  var title by remember(currentTitle) {
    mutableStateOf(TextFieldValue(currentTitle, TextRange(0, titleStemEnd(currentTitle))))
  }
  val field = remember { FocusRequester() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename notebook") },
    text = {
      // Refused, not rewritten. JupyterLab validates in this dialog and names the characters at
      // fault rather than turning `a/b` into `a-b` and handing back a notebook the user never
      // named (`packages/docmanager/src/dialogs.ts`). Since X1b the name *is* the file, so an
      // invalid one is not a matter of taste - the filesystem cannot store it.
      val refused = title.text.isNotBlank() && !isValidNotebookName(title.text)
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        isError = refused,
        supportingText = { if (refused) Text(INVALID_NAME_MESSAGE) },
        modifier = Modifier
          .focusRequester(field)
          .semantics { testTagsAsResourceId = true }
          .testTag(RENAME_FIELD_TAG),
        singleLine = true,
        label = { Text("Title") },
      )
      // Without this the dialog opens with no caret and the first keystroke goes nowhere - the
      // defect audit A1c cost a device run to find. JupyterLab asks for the same thing
      // declaratively, with `focusNodeSelector: 'input'`.
      //
      // Inside this slot, not beside the AlertDialog: the dialog's content is a composition of
      // its own in its own window, so an effect outside it runs while the field it points at is
      // not attached yet, and the request is dropped on the floor.
      LaunchedEffect(Unit) { field.requestFocus() }
    },
    confirmButton = {
      val renamed = title.text.trim()
      TextButton(
        onClick = { if (renamed == currentTitle) onDismiss() else onConfirm(renamed) },
        enabled = isValidNotebookName(renamed),
      ) {
        Text("Rename")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

/**
 * Where the stem of [title] ends, which is where the opening selection stops.
 *
 * Node's `path.extname`, which is what JupyterLab's dialog uses: the last dot starts the
 * extension, a leading dot does not (`.bashrc` is all stem), and a title with no dot is all
 * stem.
 *
 * ```
 * titleStemEnd("untitled.py")   // 8, so ".py" survives being typed over
 * titleStemEnd("Untitled")      // 8
 * ```
 */
fun titleStemEnd(title: String): Int {
  val lastDot = title.lastIndexOf('.')
  return if (lastDot <= 0) title.length else lastDot
}

/** The reference's own wording: it names the rule instead of merely greying the button out. */
private const val INVALID_NAME_MESSAGE =
  "Names must have nonzero length, cannot start with \".\", and cannot include / \\ :"
