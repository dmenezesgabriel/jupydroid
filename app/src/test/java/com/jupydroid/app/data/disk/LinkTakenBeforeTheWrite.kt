package com.jupydroid.app.data.disk

import java.io.File
import java.nio.file.Files

/**
 * A hard link to this file, made in [directory], which still names the inode the file had before
 * a write. Afterwards it reads the old content when the write replaced the file, as
 * [writeAtomically] does, and the new content when the write rewrote the file in place, which is
 * what a kill can cut in half (ADR-0089).
 *
 * ```
 * val taken = storeFile.linkTakenBeforeTheWrite(temp.root)
 * ```
 */
internal fun File.linkTakenBeforeTheWrite(directory: File): File = File(directory, "taken-before-the-write").also {
  Files.createLink(it.toPath(), toPath())
}
