package com.jupydroid.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.closeMainAreaTab
import com.jupydroid.app.documentIn
import com.jupydroid.app.mainAreaTabTitle
import com.jupydroid.app.ui.SaveBeforeCloseDialog

/**
 * `application:close`'s dirty gate, mounted once and shared by every control that empties a
 * main-area slot - the tab strip's ✕, the one-pane Up arrow, and the Running panel's own close
 * (`running:close`, `running-extension/src/index.ts`).
 *
 * Hoisted to the caller for the reason `SaveBeforeCloseGate` exists for screens: the dialog must
 * outlive the tab that opened the ask, and when the ask comes from the Running panel the tab's
 * entry may not be on screen at all - so the state lives above [TabbedDocumentPane], not in it.
 *
 * The returned lambda is `saveBeforeClose` word for word (`docmanager/src/dialogs.ts:155`): a
 * dirty document is asked about first and the close is the flush's continuation - a save that
 * cannot be made never runs it, so the document stays (`widgetmanager.ts:353-365`).
 *
 * ```
 * val closeTab = rememberMainAreaCloseGate(backStack, tabs, openDocuments, openDocumentPaths)
 * TabbedDocumentPane(…, onCloseTab = closeTab) { … }
 * ```
 */
@Composable
fun rememberMainAreaCloseGate(
  backStack: MutableList<NavKey>,
  tabs: List<NavKey>,
  openDocuments: OpenDocuments,
  openDocumentPaths: Map<NavKey, String>,
): (NavKey) -> Unit {
  var closing by remember { mutableStateOf<PendingTabClose?>(null) }
  closing?.let { pending ->
    SaveBeforeCloseDialog(
      name = pending.title,
      onSave = {
        closing = null
        pending.document.saveNow { backStack.closeMainAreaTab(pending.key, tabs) }
      },
      onDiscard = {
        closing = null
        pending.document.discardChanges()
        backStack.closeMainAreaTab(pending.key, tabs)
      },
      onCancel = { closing = null },
    )
  }
  return { key ->
    val document = key.documentIn(openDocuments)
    if (document != null && document.dirty.value) {
      closing = PendingTabClose(key, document, key.tabTitle(openDocuments, openDocumentPaths))
    } else {
      backStack.closeMainAreaTab(key, tabs)
    }
  }
}

/** What a tab's close is still deciding - the entry, its document, and the label the dialog names. */
private class PendingTabClose(val key: NavKey, val document: OpenDocument, val title: String)

/** The path a tab names - a document's, live where the document registered, else the key's own. */
private fun NavKey.tabTitle(openDocuments: OpenDocuments, liveDocumentPaths: Map<NavKey, String>): String =
  documentIn(openDocuments)?.path?.substringAfterLast('/') ?: mainAreaTabTitle(liveDocumentPaths)
