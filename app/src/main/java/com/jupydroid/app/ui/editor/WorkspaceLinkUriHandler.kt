package com.jupydroid.app.ui.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.parentDirectory
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.richtext.workspaceLinkPath
import kotlinx.coroutines.launch

/**
 * A tapped link that names a workspace path, resolved against the document at [documentPath] and
 * handed to [follow]; every other destination goes on to [outer], the handler around this one,
 * which is where a web address leaves the app (`com.jupydroid.app.RenderedLinkUriHandler`).
 *
 * ```
 * WorkspaceLinkUriHandler("reports/nb.ipynb", LocalUriHandler.current) { path -> open(path) }
 * ```
 */
internal class WorkspaceLinkUriHandler(
  private val documentPath: String,
  private val outer: UriHandler,
  private val follow: (path: String) -> Unit,
) : UriHandler {
  override fun openUri(uri: String) {
    val path = uri.workspaceLinkPath(documentPath.parentDirectory()) ?: return outer.openUri(uri)
    follow(path)
  }
}

/**
 * Goes where a workspace link points: a file opens as a document, a directory is browsed, and a
 * path with nothing there is reported.
 *
 * Checked before anything opens, as `rendermime:handle-local-link` asks `contents.get` first
 * (jupyterlab@7f0e690 `packages/rendermime-extension/src/index.ts`), so a link to nothing never
 * opens an editor on a file that is not there. Where JupyterLab then says nothing, the miss is
 * reported: a tap with no answer reads as a link that is broken, and VS Code tells the reader
 * too - its editor opens on "the file was not found".
 *
 * ```
 * workspace.followWorkspaceLink("a/notes.md", openDocument, goToFolder) { snackbar.showSnackbar(it) }
 * ```
 */
internal suspend fun WorkspaceRepository.followWorkspaceLink(
  path: String,
  openDocument: (String) -> Unit,
  goToFolder: (String) -> Unit,
  reportMissing: suspend (String) -> Unit,
) {
  when (kindAt(path)) {
    WorkspaceEntryKind.FILE -> openDocument(path)
    WorkspaceEntryKind.DIRECTORY -> goToFolder(path)
    null -> reportMissing(path)
  }
}

/**
 * What is at [path], read from its directory's listing: the port has no single-path stat, and a
 * listing is what the browser already trusts. A directory that cannot be listed holds nothing.
 */
private suspend fun WorkspaceRepository.kindAt(path: String): WorkspaceEntryKind? {
  if (path.isEmpty()) return WorkspaceEntryKind.DIRECTORY
  val siblings = runCatching { list(path.parentDirectory()) }.getOrDefault(emptyList())
  return siblings.firstOrNull { it.path == path }?.kind
}

/** What a link to nothing says, naming the path it resolved to - where a mistyped `../` shows. */
internal fun missingLinkMessage(path: String): String = "No file at $path"

/**
 * Makes every workspace link drawn inside [content] follow [followWorkspaceLink], and every
 * `#heading` link ask [jumpToHeadingAnchor], which answers whether it found the heading; either
 * miss is said on [snackbar]. Provided by the screen that owns the document, since only it knows
 * where the document lives now, what its headings are, and has somewhere to say a miss.
 *
 * ```
 * WorkspaceLinksFollowed(state.notebookId, workspace, openDocument, onGoToFolder, jumpToAnchor, snackbar) { ... }
 * ```
 */
@Composable
internal fun WorkspaceLinksFollowed(
  documentPath: String,
  workspaceRepository: WorkspaceRepository,
  openDocument: (String) -> Unit,
  onGoToFolder: (String) -> Unit,
  jumpToHeadingAnchor: (fragment: String) -> Boolean,
  snackbar: SnackbarHostState,
  content: @Composable () -> Unit,
) {
  val outer = LocalUriHandler.current
  val scope = rememberCoroutineScope()
  val currentOpenDocument by rememberUpdatedState(openDocument)
  val currentGoToFolder by rememberUpdatedState(onGoToFolder)
  val currentJumpToHeadingAnchor by rememberUpdatedState(jumpToHeadingAnchor)
  val links = remember(documentPath, outer, workspaceRepository, snackbar) {
    val workspaceLinks = WorkspaceLinkUriHandler(documentPath, outer) { path ->
      scope.launch {
        workspaceRepository.followWorkspaceLink(path, currentOpenDocument, currentGoToFolder) {
          snackbar.showSnackbar(missingLinkMessage(it))
        }
      }
    }
    HeadingLinkUriHandler(workspaceLinks) { fragment ->
      if (!currentJumpToHeadingAnchor(fragment)) scope.launch { snackbar.showSnackbar(missingHeadingMessage(fragment)) }
    }
  }
  CompositionLocalProvider(LocalUriHandler provides links, content = content)
}
