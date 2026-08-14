package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupydroid.app.ui.RenameFileDialog

/** Small enough to read as part of the title rather than as a button beside it. */
private val TITLE_EDIT_ICON_SIZE = 18.dp

/**
 * The notebook's app bar: back and the title.
 *
 * Run All lives on [NotebookBar] now, not here. It is a kernel action like interrupt and restart,
 * it belongs beside them rather than alone at the far corner of the screen, and the bottom of a
 * phone is where a thumb already is.
 *
 * **The title is the rename target**, which is where JupyterLab puts it too - `docmanager:rename`
 * is on the document-title context menu, `"selector": "[data-type=\"document-title\"]"`
 * (`packages/docmanager-extension/schema/plugin.json:73-77`). A tap is the touch analogue of
 * that right-click, and the pencil beside it is what says so: a gesture with nothing on screen
 * announcing it is undiscoverable, which is the lesson the cell menu cost three attempts to
 * learn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
  title: String,
  onRename: (String) -> Unit,
  onBack: () -> Unit,
) {
  var renameOpen by remember { mutableStateOf(false) }

  TopAppBar(
    title = { RenameableTitle(title = title, onClick = { renameOpen = true }) },
    navigationIcon = {
      IconButton(onClick = onBack) {
        // Named for where it goes, not for the gesture. "Back" is also the system navigation
        // bar's button, and a `uiautomator` dump has both - so five Maestro flows meaning this
        // arrow have been tapping the system one and passing anyway, because before the modal
        // back of U2 the two did the same thing. They stopped agreeing, which is how it was
        // found (`evidence/u-flow-sweep/`). A destination-naming label is also what TalkBack
        // wants: "Back" leaves a screen reader to guess back to what.
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to notebooks")
      }
    },
  )

  if (renameOpen) {
    RenameFileDialog(
      currentTitle = title,
      onConfirm = { renamed ->
        renameOpen = false
        onRename(renamed)
      },
      onDismiss = { renameOpen = false },
    )
  }
}

@Composable
private fun RenameableTitle(title: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.clickable(onClickLabel = "Rename notebook", onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(modifier = Modifier.width(8.dp))
    Icon(
      imageVector = Icons.Filled.Edit,
      // The whole row is one target, so the icon does not announce itself separately - naming
      // it again would give a screen reader two things to say about one control.
      contentDescription = null,
      modifier = Modifier.size(TITLE_EDIT_ICON_SIZE),
      tint = LocalContentColor.current,
    )
  }
}
