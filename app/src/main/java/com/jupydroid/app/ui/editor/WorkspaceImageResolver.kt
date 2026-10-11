package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.jupydroid.app.domain.model.parentDirectory
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.richtext.workspaceLinkPath

/**
 * An image source naming a workspace file, read from [workspaceRepository]; every other source goes
 * on to [outer].
 *
 * The source is resolved the way a link's destination is (`workspaceLinkPath`): against the
 * directory of the document at [documentPath], as `RenderMimeRegistry.UrlResolver` resolves an
 * `<img>`'s `src` for `handleAttr` (jupyterlab@7f0e690 packages/rendermime/src/registry.ts,
 * packages/rendermime/src/renderers.ts). A local path that names no readable file is nothing, so
 * its alt draws; it is not handed on, since no resolver further out reads the workspace. Read
 * through the port, whose disk adapter reads off the main thread.
 *
 * ```
 * WorkspaceImageResolver("reports/nb.ipynb", workspace, outer).imageBytes("img/plot.png")
 * ```
 */
class WorkspaceImageResolver(
  private val documentPath: String,
  private val workspaceRepository: WorkspaceRepository,
  private val outer: RichImageResolver,
) : RichImageResolver {
  override suspend fun imageBytes(source: String): ByteArray? {
    val path = source.workspaceLinkPath(documentPath.parentDirectory()) ?: return outer.imageBytes(source)
    return runCatching { workspaceRepository.readBytes(path) }.getOrNull()
  }
}

/**
 * Makes images drawn inside [content] that name a workspace path read it, resolved against the
 * document at [documentPath], around whatever resolver is already provided. Provided by the screen
 * that owns the document, since only it knows where the document lives now.
 *
 * ```
 * WorkspaceImagesResolved(state.notebookId, workspaceRepository) { Scaffold(...) }
 * ```
 */
@Composable
internal fun WorkspaceImagesResolved(
  documentPath: String,
  workspaceRepository: WorkspaceRepository,
  content: @Composable () -> Unit,
) {
  val outer = LocalRichImageResolver.current
  val resolver = remember(documentPath, workspaceRepository, outer) {
    WorkspaceImageResolver(documentPath, workspaceRepository, outer)
  }
  CompositionLocalProvider(LocalRichImageResolver provides resolver, content = content)
}
