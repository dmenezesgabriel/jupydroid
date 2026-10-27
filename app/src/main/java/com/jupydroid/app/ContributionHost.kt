package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.ui.shell.RichTextMarkdownRenderer
import com.jupydroid.extension.api.SidePanelHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus

/**
 * What the shell tells an extension's body about where it is drawn, the same for a contributed
 * side panel and a contributed tab, so a chat answers the same in either.
 *
 * ```
 * val host = rememberContributionHost(appContainer, spacePorts, browsedDirectory, notebookCells, openDocument)
 * ContributedSidePanel(contribution, host)
 * ```
 */
@Composable
internal fun rememberContributionHost(
  appContainer: AppContainer,
  spacePorts: EnteredSpacePorts,
  browsedDirectory: String,
  notebookCells: OpenNotebookCells,
  openDocument: (String) -> Unit,
): SidePanelHost {
  val fileOpener = remember(spacePorts.workspaceFiles, openDocument) {
    EditorWorkspaceFileOpener(
      spacePorts.workspaceFiles,
      appContainer.applicationScope + Dispatchers.Main,
      openDocument,
    )
  }
  return SidePanelHost(
    browsedDirectory,
    spacePorts.extensionServer,
    spacePorts.space.id,
    RichTextMarkdownRenderer,
    notebookCells,
    fileOpener,
  )
}
