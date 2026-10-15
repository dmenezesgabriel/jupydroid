package com.jupydroid.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.documentIn
import kotlinx.coroutines.flow.combine

/**
 * `docmanager:save-all`: every open document that owes a write is saved, in [tabs]' order.
 *
 * **One at a time, stopping at the first refusal**, where jupyterlab@7f0e690 saves every context at
 * once (`packages/docmanager-extension/src/index.tsx`, `CommandIDs.saveAll`) and asks each conflict
 * modally wherever the user is (`packages/docregistry/src/context.ts`, `_raiseConflict`). Here only
 * the top tab composes, so a refused document's File Changed question or error line has nowhere to
 * draw beneath it: the refused tab is handed to [reveal], brought to the top to ask, and the tabs
 * after it keep their dots until Save All is pressed again.
 *
 * A clean document is passed over - VS Code's `DirtyWorkingCopiesContext` rule
 * (vscode@3f3380c `contrib/files/browser/fileCommands.ts`) - since rewriting it moves its mtime for
 * nothing (ADR-0093). So is a tab whose document never registered: it has not composed since
 * launch, so it holds no edit.
 *
 * ```
 * applicationScope.launch { saveAllOpenDocuments(orderedTabs, openDocuments, backStack::activateMainAreaTab) }
 * ```
 */
suspend fun saveAllOpenDocuments(tabs: List<NavKey>, openDocuments: OpenDocuments, reveal: (NavKey) -> Unit) {
  val refused = tabs.firstOrNull { tab ->
    val document = tab.documentIn(openDocuments)
    document != null && document.dirty.value && !document.saveAndConfirm()
  }
  refused?.let(reveal)
}

/**
 * Whether Save All has anything to write: any open document dirty, followed live so the button
 * greys the moment the last dot clears. `combine` over no flows emits nothing, so an empty
 * registry reads as the initial false.
 *
 * ```
 * RunningSidePanel(…, saveAllEnabled = anyOpenDocumentDirty(openDocuments), …)
 * ```
 */
@Composable
fun anyOpenDocumentDirty(openDocuments: OpenDocuments): Boolean {
  val documents = openDocuments.all
  val anyDirty = remember(documents) { combine(documents.map { it.dirty }) { flags -> flags.any { it } } }
  val dirty by anyDirty.collectAsStateWithLifecycle(initialValue = false)
  return dirty
}
