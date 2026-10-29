package com.jupydroid.app.data.disk

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Deletes [root] and everything under it as `shutil.rmtree` would: depth first, and a link it meets
 * is unlinked rather than walked, so nothing outside [root] is reached. A missing [root] is no-op.
 *
 * ```
 * deleteTreeUnlinkingLinks(File(spaceDir, "usr").toPath())
 * ```
 */
fun deleteTreeUnlinkingLinks(root: Path) {
  if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
  Files.walkFileTree(root, DeletingVisitor)
}

/** Deletes what it visits; without `FOLLOW_LINKS` a link is visited as a file and so unlinked. */
private object DeletingVisitor : SimpleFileVisitor<Path>() {
  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    Files.delete(file)
    return FileVisitResult.CONTINUE
  }

  override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
    if (exc != null) throw exc
    Files.delete(dir)
    return FileVisitResult.CONTINUE
  }
}
