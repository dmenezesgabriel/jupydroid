package com.jupydroid.app.ui.shell

import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.isValidWorkspacePath
import com.jupydroid.app.domain.model.parentDirectory
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.catchingRefusal

/** How a Save As ended: written, stopped at a taken path to ask, or refused by the disk with its reason. */
sealed interface SaveAsOutcome {
  data object Written : SaveAsOutcome

  data object Taken : SaveAsOutcome

  data class Refused(val message: String) : SaveAsOutcome
}

/**
 * `docmanager:save-as` short of its dialogs: [document] as it is on screen is written to [target],
 * unless something already holds it and the user has not yet answered [overwrite] - jupyterlab@7f0e690
 * probes the path and asks "File Overwrite?" before `_finishSaveAs` writes
 * (`packages/docregistry/src/context.ts`, `saveAs`, `_maybeOverWrite`). A write the disk refuses
 * comes back with its reason, which the reference shows as `File Save Error for %1`.
 *
 * ```
 * when (saveDocumentAs(document, "reports/copy.py", workspaceRepository)) { … }
 * ```
 */
suspend fun saveDocumentAs(
  document: OpenDocument,
  target: String,
  workspace: WorkspaceRepository,
  overwrite: Boolean = false,
): SaveAsOutcome {
  val attempt = catchingRefusal {
    if (!overwrite && workspace.holds(target)) return@catchingRefusal SaveAsOutcome.Taken
    document.writeCopyTo(target)
    SaveAsOutcome.Written
  }
  return attempt.getOrElse { SaveAsOutcome.Refused(it.message ?: "could not save '${document.path}' as '$target'") }
}

/**
 * Whether anything already holds [path] - the reference's probe, `contents.get(newPath, {content:
 * false})` and its 404, answered from the folder's listing. The listing leaves hidden entries out,
 * and [isValidWorkspacePath] refuses a hidden name, so for every path the dialog lets through it
 * is exact; a link pointing nowhere is still listed, and a folder that is not there is refused
 * here, before anything is written.
 */
private suspend fun WorkspaceRepository.holds(path: String): Boolean = list(path.parentDirectory()).any {
  it.path ==
    path
}
