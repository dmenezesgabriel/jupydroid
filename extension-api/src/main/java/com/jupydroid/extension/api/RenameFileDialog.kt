package com.jupydroid.extension.api

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

public const val RENAME_FIELD_TAG: String = "rename-field"

/**
 * Renames a file, from Recents, from the document itself, from the browser's row menu, or a chat
 * from the Chat panel's list.
 *
 * One dialog for all three, because two would drift: JupyterLab keeps a single
 * `DocumentManagerDialogs.rename` and reaches it from the File menu and from the document title
 * alike (`packages/docmanager/src/dialogs.ts:55-100`). Its own title is "Rename File" and its
 * accept button is "Rename" (`dialogs.ts:65-71`), which is the split [heading] keeps: what is
 * being renamed varies, what the button does not - unless the dialog names something new, where
 * [confirmLabel] says "Create", as VS Code's new-profile button does.
 *
 * A name that is not a file's passes its own [isValidName], [refusalMessage] and
 * [initialSelection]: a Space's name is a label in its `config.json`, so only a blank one is
 * refused and there is no extension to keep out of the selection
 * (`app/src/main/java/com/jupydroid/app/ui/spaces/SpacesHomeScreen.kt`). The focus fix and the
 * unchanged-is-dismiss rule stay one copy.
 *
 * In the contract, so an extension asks for a name through this same dialog rather than one of its
 * own (ADR-0104): jupyter-chat renames a chat through apputils' `InputDialog.getText`, the host's
 * (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts`, `renameChat`).
 *
 * [onConfirm] is called **only when the title actually changed**. That is JupyterLab's rule -
 * `if (!newPath || newPath === fileName) return null`, silently, with no error - and it matters
 * more here than there: a write is what makes a notebook "updated", so confirming a dialog
 * without editing would push the notebook to the top of Recents for nothing. An unchanged or
 * blank title takes [onDismiss] instead, so the caller still closes the dialog either way.
 *
 * ```
 * if (renameOpen) {
 *   RenameFileDialog(
 *     currentTitle = path.substringAfterLast('/'),
 *     onConfirm = { renameOpen = false; scope.launch { workspaceFiles.rename(path, it) } },
 *     onDismiss = { renameOpen = false },
 *   )
 * }
 * ```
 */
@Composable
public fun RenameFileDialog(
  currentTitle: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
  heading: String = "Rename notebook",
  isValidName: (String) -> Boolean = ::isValidFileName,
  refusalMessage: String = INVALID_NAME_MESSAGE,
  initialSelection: (String) -> TextRange = { TextRange(0, titleStemEnd(it)) },
  confirmLabel: String = "Rename",
  /** What the field holds - a name, or a whole path where the caller takes one. */
  fieldLabel: String = "Name",
) {
  // Selected, not merely filled: typing should replace the name and leave `.py` alone.
  var title by remember(currentTitle) {
    mutableStateOf(TextFieldValue(currentTitle, initialSelection(currentTitle)))
  }
  val field = remember { FocusRequester() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(heading) },
    text = {
      // Refused, not rewritten. JupyterLab validates in this dialog and names the characters at
      // fault rather than turning `a/b` into `a-b` and handing back a notebook the user never
      // named (`packages/docmanager/src/dialogs.ts`). The name *is* the file here, so an
      // invalid one is not a matter of taste - the filesystem cannot store it.
      val refused = title.text.isNotBlank() && !isValidName(title.text)
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        isError = refused,
        supportingText = { if (refused) Text(refusalMessage) },
        modifier = Modifier
          .focusRequester(field)
          .semantics { testTagsAsResourceId = true }
          .testTag(RENAME_FIELD_TAG),
        singleLine = true,
        label = { Text(fieldLabel) },
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
        enabled = isValidName(renamed),
      ) {
        Text(confirmLabel)
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
public fun titleStemEnd(title: String): Int {
  val lastDot = title.lastIndexOf('.')
  return if (lastDot <= 0) title.length else lastDot
}

/** The reference's own wording: it names the rule instead of merely greying the button out. */
private const val INVALID_NAME_MESSAGE =
  "Names must have nonzero length, cannot start with \".\", and cannot include / \\ :"
