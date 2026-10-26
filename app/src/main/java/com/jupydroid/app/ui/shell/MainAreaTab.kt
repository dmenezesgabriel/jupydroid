package com.jupydroid.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.Console
import com.jupydroid.app.ContributedDocument
import com.jupydroid.app.Editor
import com.jupydroid.app.FileEditor
import com.jupydroid.app.Launcher
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.Settings
import com.jupydroid.app.Terminal
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.DirtyMarker
import com.jupydroid.extension.api.DocumentContribution

/**
 * One `lm-TabBar-tab`: the kind's icon, the `title.label`, and the close icon that swaps for the
 * `jp-mod-dirty` busy dot while the document owes a write (`ui-components/style/icons.css:268-278`).
 * The dot stays the close *button* - the reference hides the swap behind `:hover`, and a touch
 * screen has none, so here the dot itself is what a dirty tab's close is tapped on.
 *
 * A long press is the tab's context menu - the press is the only gesture left once click
 * activates. Every tab's menu leads with `application:close-other-tabs` and
 * `application:close-right-tabs`, bound to every main-area tab at ranks 4 and 5 (jupyterlab@7f0e690
 * `packages/application-extension/schema/commands.json` `context`) - the close group VS Code's
 * `EditorTitleContext` and IntelliJ's `EditorTabPopupMenu` lead with too (ADR-0103). Beneath them, a document
 * tab holds `docmanager:rename`, the `document-title`-bound command,
 * Save As, which VS Code keeps on the same menu (vscode@3f3380c
 * `src/vs/workbench/contrib/files/browser/fileActions.contribution.ts`, `SAVE_FILE_AS_COMMAND_ID`),
 * `docmanager:restore-checkpoint`, which the reference keeps in the File menu beside them, and
 * `docmanager:duplicate` and `docmanager:delete`, which it binds to the tab with rename. A tab an
 * extension draws may move to its side panel instead, jupyter-chat's "Move the chat to the side panel"
 * (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts`, `moveChat`).
 */
@Composable
internal fun MainAreaTab(
  /** `title.icon` - see [tabIcon]. */
  icon: ImageVector,
  title: String,
  document: OpenDocument?,
  active: Boolean,
  closable: Boolean,
  renameable: Boolean,
  width: Dp,
  /** `closeOtherTabs.isEnabled`: a second tab exists. */
  canCloseOthers: Boolean,
  /** `closeRightTabs.isEnabled`: a tab exists after this one. */
  canCloseRight: Boolean,
  onActivate: () -> Unit,
  onClose: () -> Unit,
  onCloseOthers: () -> Unit,
  onCloseRight: () -> Unit,
  onRename: () -> Unit,
  onSaveAs: () -> Unit,
  onRevert: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  /** `moveChat` from the main area, or null for a tab whose contribution gives no side panel to move to. */
  onMoveToSidePanel: (() -> Unit)? = null,
) {
  val chrome = chromePalette
  var menuOpen by remember { mutableStateOf(false) }
  val dirty by document?.dirty?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
  Column(modifier = Modifier.width(width)) {
    // `jp-mod-current::before` / `tab.activeBorderTop` - the brand line the current tab wears.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(ACTIVE_EDGE)
        .background(if (active) chrome.tabActiveBorderTop else Color.Transparent),
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .height(TAB_HEIGHT)
        .background(if (active) chrome.tabActiveBackground else chrome.tabInactiveBackground)
        .trailingBorder(chrome.tabBorder)
        .semantics {
          contentDescription = "Tab: $title"
          selected = active
        }
        .combinedClickable(
          onClick = onActivate,
          onLongClick = { menuOpen = true },
          onLongClickLabel = "Tab actions",
        )
        .padding(start = Spacing.sm, end = Spacing.xs),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(TAB_ICON),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Cleared, not merged: the row already announces itself as "Tab: $title", and a bare
        // text node here is the document's title a second time - which turns every
        // `onNodeWithText(title)` in the navigation tests into a two-node match.
        modifier = Modifier.padding(start = Spacing.xs).weight(1f).clearAndSetSemantics {},
      )
      if (closable) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(TAB_CLOSE_TOUCH)
            .semantics { contentDescription = "Close tab: $title" }
            .clickable(onClick = onClose),
        ) {
          if (dirty) {
            DirtyMarker()
          } else {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(TAB_ICON),
            )
          }
        }
      }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text("Close All Other Tabs") },
        enabled = canCloseOthers,
        onClick = {
          menuOpen = false
          onCloseOthers()
        },
      )
      DropdownMenuItem(
        text = { Text("Close Tabs to Right") },
        enabled = canCloseRight,
        onClick = {
          menuOpen = false
          onCloseRight()
        },
      )
      if (onMoveToSidePanel != null) {
        HorizontalDivider()
        DropdownMenuItem(
          text = { Text("Move to side panel") },
          onClick = {
            menuOpen = false
            onMoveToSidePanel()
          },
        )
      }
      // `docmanager:*` binds to `[data-type="document-title"]` alone: a tab without a path stops here.
      if (!renameable) return@DropdownMenu
      HorizontalDivider()
      DropdownMenuItem(
        text = { Text("Rename…") },
        onClick = {
          menuOpen = false
          onRename()
        },
      )
      // Greyed for a tab whose document has not registered: nothing is held on screen to copy.
      DropdownMenuItem(
        text = { Text("Save As…") },
        enabled = document != null,
        onClick = {
          menuOpen = false
          onSaveAs()
        },
      )
      DropdownMenuItem(
        text = { Text("Revert to Checkpoint…") },
        enabled = document != null,
        onClick = {
          menuOpen = false
          onRevert()
        },
      )
      DropdownMenuItem(
        text = { Text("Duplicate") },
        enabled = document != null,
        onClick = {
          menuOpen = false
          onDuplicate()
        },
      )
      DropdownMenuItem(
        text = { Text("Delete…") },
        enabled = document != null,
        onClick = {
          menuOpen = false
          onDelete()
        },
      )
    }
  }
}

/**
 * `title.icon` - the kind's own glyph (its `filetype` SVG), in the Material equivalents the app
 * draws. `internal` because the Running panel's Open Tabs row wears the same one - `OpenTab.icon()`
 * returns `widget.title.icon`, the strip's own property (`running-extension/src/opentabs.ts:110-112`).
 * A contributed document wears the glyph its contribution gave, found by the id its key carries.
 */
internal fun tabIcon(key: NavKey, documentContributions: List<DocumentContribution>): ImageVector = when (key) {
  is Editor -> Icons.AutoMirrored.Filled.MenuBook
  is FileEditor -> Icons.AutoMirrored.Filled.InsertDriveFile
  is Terminal -> Icons.Filled.Terminal
  is Console -> Icons.Filled.Code
  Launcher -> Icons.AutoMirrored.Filled.OpenInNew
  Settings -> Icons.Filled.Settings
  is ContributedDocument -> documentContributions.find { it.id == key.contributionId }?.icon
    ?: Icons.AutoMirrored.Filled.InsertDriveFile
  else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/**
 * The `filetype` glyph's slot - a step under the reference's `jp-icon3` 16px so the ellipsized
 * label keeps the extra room where a tab is squeezed to the 80dp floor.
 */
private val TAB_ICON = 14.dp

/** A ✕-sized slot - JupyterLab's close icon is ~16px; the tap target is the slot around it. */
private val TAB_CLOSE_TOUCH = 24.dp
