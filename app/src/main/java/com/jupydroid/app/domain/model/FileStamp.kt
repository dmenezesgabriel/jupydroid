package com.jupydroid.app.domain.model

/**
 * What a stored file looked like when it was read or written: enough to tell, at the next save,
 * whether anything else has written it since.
 *
 * The modification time and the size, which is vscode@3f3380c6's `etag`
 * (`src/vs/platform/files/common/files.ts`) and for its reason: a content hash would read the
 * whole file back on every save. Compared for equality and never for order - a file put back from
 * an older copy is as much a change as a newer one.
 *
 * ```
 * val opened = repository.readStampedNotebook("Analysis.ipynb")
 * val saved: FileStamp = repository.saveNotebook(edited, expected = opened.stamp)
 * ```
 */
data class FileStamp(val modifiedNanos: Long, val size: Long)

/**
 * A notebook and the [FileStamp] of the file it was read from.
 *
 * Beside the notebook rather than on it: [Notebook] carries no time on purpose, since when it was
 * written is a fact about the stored file and not about the cells.
 *
 * ```
 * val (notebook, stamp) = repository.readStampedNotebook("Analysis.ipynb")
 * ```
 */
data class StampedNotebook(val notebook: Notebook, val stamp: FileStamp)
