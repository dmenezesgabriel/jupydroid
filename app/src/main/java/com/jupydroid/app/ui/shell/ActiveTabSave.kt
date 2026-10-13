package com.jupydroid.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.OpenDocument

/**
 * `docmanager:save` as something to press: the document on top is written and stays open.
 *
 * **On the strip, where neither reference puts it.** JupyterLab draws a save button on the
 * notebook's toolbar and gives a plain file only a menu row and a key
 * (`packages/notebook/src/default-toolbar.tsx`, `createSaveButton`); VS Code puts Save on the
 * tab's context menu (`files/browser/fileActions.contribution.ts`). Here the file editor has no
 * bar of its own and a long press is a gesture nobody is shown, while the strip is drawn over
 * every document and already wears the dirty dot - so what says a document owes a write and what
 * writes it sit side by side.
 *
 * **Live only while [document] is dirty**, which is VS Code's `ActiveEditorDirtyContext` rather
 * than JupyterLab's "while writable": saving a clean document rewrites the same bytes under a new
 * modification time, which anything else holding the file open then reads as a change. Greyed
 * rather than removed, and drawn for a tab that is not a document at all, so the strip keeps its
 * shape whichever tab is on top.
 *
 * It reports nothing of its own. A write that succeeds turns the dot back into the close; one that
 * is refused is drawn by the document's screen, which is composed because its tab is on top.
 *
 * ```
 * ActiveTabSave(title = "notes.py", document = openDocuments.openedAs(DocumentOpening("notes.py")), height = 34.dp)
 * ```
 */
@Composable
internal fun ActiveTabSave(title: String, document: OpenDocument?, height: Dp, modifier: Modifier = Modifier) {
  val dirty by document?.dirty?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
  val tint = MaterialTheme.colorScheme.onSurfaceVariant
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(width = TRAILING_SLOT_WIDTH, height = height)
      .semantics { contentDescription = "Save tab: $title" }
      .clickable(enabled = dirty, role = Role.Button) { document?.saveNow {} },
  ) {
    Icon(
      imageVector = Icons.Filled.Save,
      contentDescription = null,
      tint = if (dirty) tint else tint.copy(alpha = DISABLED_ICON_ALPHA),
      modifier = Modifier.size(TRAILING_ICON),
    )
  }
}

/**
 * Wider than the strip is tall: the slot is the whole tap target, and it sits at the window's edge.
 * Shared with [ActiveTabReload], so the strip's trailing controls are one size.
 */
internal val TRAILING_SLOT_WIDTH = 40.dp

/** `jp-icon3`'s 16px, a step over a tab's own glyph because this one is pressed rather than read. */
internal val TRAILING_ICON = 18.dp

/** Material's disabled-content opacity, the one `IconButton` applies to a disabled icon. */
internal const val DISABLED_ICON_ALPHA = 0.38f
