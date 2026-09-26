package com.jupydroid.app.ui.explorer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jupydroid.app.ui.shell.sideBarTopAppBarColors

/**
 * What the screen is, above the breadcrumb that says where in it you are.
 *
 * Two rows rather than one: the title is fixed and the crumbs scroll, and putting them together
 * would make the title scroll away with them - on a phone that is most of the bar's width gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilesBar(onBack: (() -> Unit)?, onCreate: (NewItem) -> Unit, onOpenSettings: () -> Unit) {
  TopAppBar(
    title = { Text("Files") },
    colors = sideBarTopAppBarColors(),
    navigationIcon = {
      onBack?.let {
        IconButton(onClick = it) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recents")
        }
      }
    },
    actions = {
      NewMenu(onCreate = onCreate)
      IconButton(onClick = onOpenSettings) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings")
      }
    },
  )
}

/**
 * The reference's New drop-down, on a phone: a "+" that opens Notebook / Python file / Folder.
 *
 * The reference offers the same three from the filebrowser toolbar's New widget and then renames
 * the result in place (`browser.ts:_createNew` -> `rename()`). Ours lands the creation in the
 * browsed directory under the server's own `untitled{ext}` name and leaves the rename to the row's
 * own menu, which X2f gave it - one way to name a thing rather than a second one that only new
 * files can reach.
 */
@Composable
private fun NewMenu(onCreate: (NewItem) -> Unit) {
  var open by remember { mutableStateOf(false) }
  IconButton(onClick = { open = true }) {
    Icon(Icons.Filled.Add, contentDescription = "New")
  }
  DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
    NEW_MENU_ITEMS.forEach { (item, label) ->
      DropdownMenuItem(text = { Text(label) }, onClick = {
        open = false
        onCreate(item)
      })
    }
  }
}

/** The three the reference's New widget offers, in its order. */
private val NEW_MENU_ITEMS = listOf(
  NewItem.NOTEBOOK to "New notebook",
  NewItem.PYTHON_FILE to "New Python file",
  NewItem.FOLDER to "New folder",
)
