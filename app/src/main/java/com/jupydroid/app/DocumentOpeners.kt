package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import kotlinx.coroutines.launch

/**
 * Opening a document, wherever it was opened from: the right editor, and a line in Recents.
 *
 * **One opener rather than one per screen.** Home knew which of its rows was a notebook and the
 * explorer worked it out from the suffix, so the same rule lived in two places and only one of
 * them could record a recent. The reference has a single `docmanager:open` for the same reason -
 * the path decides the factory, and recents are recorded once, from the widget manager's
 * `activate-request` hook (`packages/docmanager/src/widgetmanager.ts:268-285`).
 *
 * A `.ipynb` goes to the notebook editor and anything else to the plain one, which is the
 * reference's own split: the notebook factory claims its extension and the file editor is the
 * default for the rest (`packages/fileeditor`).
 *
 * Recorded on [AppContainer.applicationScope], not on a screen's: the write must finish even if
 * the tap that caused it is the last thing that screen does.
 */
@Composable
internal fun rememberDocumentOpener(backStack: MutableList<NavKey>, appContainer: AppContainer): (String) -> Unit =
  remember(backStack, appContainer) {
    { path ->
      if (path.endsWith(NOTEBOOK_SUFFIX, ignoreCase = true)) {
        backStack.openNotebook(path)
      } else {
        backStack.openFile(path)
      }
      appContainer.applicationScope.launch { appContainer.recentDocuments.recordOpening(path) }
    }
  }

/**
 * `terminal:create-new`, adapted: the reference reaches it from the Launcher tab or the File
 * menu, and this app has neither. So this is the
 * explorer's own provisional reach point - a second icon beside the New notebook/file/folder
 * menu rather than a fourth item inside it, since that menu's own KDoc already states parity
 * with the reference's New widget (exactly three, none of them a terminal).
 *
 * A separate `rememberCoroutineScope` rather than [AppContainer.applicationScope]: opening a
 * terminal only matters while this screen exists to navigate away from, which is
 * `architecture.md`'s own line for what belongs on which scope - unlike recording a recent or
 * flushing an edit, nothing here has to outlive the composition that asked for it.
 */
@Composable
internal fun rememberNewTerminal(backStack: MutableList<NavKey>, appContainer: AppContainer): (String) -> Unit {
  val scope = rememberCoroutineScope()
  return remember(backStack, appContainer, scope) {
    { cwd ->
      scope.launch {
        val session = appContainer.terminalGateway.open(cwd.ifEmpty { null })
        backStack.openTerminal(session.name)
      }
    }
  }
}
