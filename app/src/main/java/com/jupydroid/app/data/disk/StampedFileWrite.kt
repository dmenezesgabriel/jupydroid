package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.port.FileChangedOnDiskException
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

/**
 * This file's [FileStamp] now. A symlink answers for the file it names, which is the one a save
 * replaces (`File.writeAtomically`).
 *
 * ```
 * val read = File(rootDir, "notes.txt").stamp()
 * ```
 */
internal fun File.stamp(): FileStamp {
  val attributes = Files.readAttributes(toPath(), BasicFileAttributes::class.java)
  return FileStamp(attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS), attributes.size())
}

/**
 * Replaces this file with [bytes] unless it changed since [expected] was taken, and answers with
 * the stamp it has afterwards.
 *
 * Any difference refuses, with no margin: jupyterlab@7f0e690 allows the disk to be 500 ms newer
 * (`packages/docregistry/src/context.ts`, `lastModifiedCheckMargin`) for filesystems that report
 * inconsistent times, and on this local disk that margin would hide a cell's write landing just
 * after a save. What keeps a `touch` from asking is vscode@3f3380c6's last step instead
 * (`src/vs/platform/files/common/fileService.ts`, `validateWriteFile`): a file already holding
 * exactly [bytes] is written.
 *
 * The check and the rename are two steps, so a writer landing between them is still replaced, as
 * it is in both references; closing that needs a lock the kernel would have to honour.
 *
 * ```
 * stamp = file.writeIfUnchanged(stamp, text.toByteArray(), named = "reports/notes.txt")
 * ```
 *
 * @param expected null to write whatever is there.
 * @param named the path the refusal reports - the workspace's, not the device's.
 * @throws FileChangedOnDiskException when the file is not the one [expected] describes.
 */
internal fun File.writeIfUnchanged(expected: FileStamp?, bytes: ByteArray, named: String): FileStamp {
  if (expected != null) refuseIfChangedSince(expected, bytes, named)
  writeAtomically { it.write(bytes) }
  return stamp()
}

/** A file that is gone has nothing to lose, so it is written back - `_maybeSave` saves on a 404. */
private fun File.refuseIfChangedSince(expected: FileStamp, bytes: ByteArray, named: String) {
  if (!exists()) return
  val found = stamp()
  if (found == expected || holds(bytes)) return
  throw FileChangedOnDiskException(named, expected, found)
}

/** Length first, so a save over a changed file reads it back only when it could be the same. */
private fun File.holds(bytes: ByteArray): Boolean = length() == bytes.size.toLong() && readBytes().contentEquals(bytes)
