package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.FileStamp
import kotlinx.coroutines.flow.Flow

/**
 * Hears the workspace's files being written, by anything - the kernel, the terminal, a second
 * client, and this app too.
 *
 * Beside [WorkspaceFiles.changes] rather than on it, because that is "what this app moved":
 * the repository announces its own acts, and this reports the disk's. vscode@3f3380c keeps the same
 * split - `IFileService.onDidRunOperation` for its own acts, `onDidFilesChange` for the watcher's
 * (`src/vs/platform/files/common/files.ts`).
 *
 * The report carries the file's [FileStamp] as it was when the write was heard, because the writer
 * is not named: a holder recognises its own save by the stamp that save answered, as vscode's
 * resolve recognises an unchanged file by its etag
 * (`src/vs/workbench/services/textfile/common/textFileEditorModel.ts`, `resolveFromFile`).
 *
 * ```
 * workspaceWatcher.writesTo("reports/notes.txt").collect { stamp -> if (stamp != held) reread() }
 * ```
 */
interface WorkspaceWatcher {
  /**
   * Each write that finished on [path], workspace-relative, and each file renamed onto it, until
   * the collector stops. Nothing is replayed: a write before the collect is not reported.
   */
  fun writesTo(path: String): Flow<FileStamp>
}
