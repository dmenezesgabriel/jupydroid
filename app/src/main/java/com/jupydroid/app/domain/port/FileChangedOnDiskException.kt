package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.FileStamp

/**
 * A save was refused because the file is no longer the one that was read.
 *
 * The workspace is the kernel's too, so a cell can rewrite a file an editor has open, and a save
 * that went ahead would silently replace what the cell wrote. vscode@3f3380c6 refuses the same
 * write as `FILE_MODIFIED_SINCE` (`src/vs/platform/files/common/fileService.ts`,
 * `validateWriteFile`), and jupyterlab@7f0e690 raises its **File Changed** dialog for it
 * (`packages/docregistry/src/context.ts`, `_maybeSave`).
 *
 * A type of its own so the caller can ask the user for exactly this and report every other failure
 * as a failure.
 *
 * ```
 * try { repository.saveNotebook(notebook, expected = stamp) }
 * catch (e: FileChangedOnDiskException) { askRevertOrOverwrite() }
 * ```
 */
class FileChangedOnDiskException(path: String, expected: FileStamp, found: FileStamp) :
  IllegalStateException("'$path' changed on disk since it was read: expected $expected, found $found")
