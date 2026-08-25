package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.inWorkspaceOrder
import java.io.File
import java.time.Instant

/**
 * What a directory on disk holds, hidden entries left out and already in the browser's order.
 *
 * One home, because two things read the tree now: `DiskWorkspaceRepository` lists it for the
 * explorer and `DiskWorkspaceSearcher` walks it for a query. Three rules have to agree between
 * them - which entries are hidden, how a path is built from a parent, and what order they come in -
 * and a second copy is how they stop agreeing.
 *
 * ```
 * File(root, "reports").workspaceEntriesUnder("reports")
 * ```
 *
 * @receiver the directory itself.
 * @param parentPath its own workspace-relative path, with the root spelled as the empty string.
 */
internal fun File.workspaceEntriesUnder(parentPath: String): List<WorkspaceEntry> = (listFiles() ?: emptyArray())
  // `ContentsManager.allow_hidden` is false by default, so the reference's browser leaves these
  // out too - and a search result naming one would be a result nobody could open.
  .filterNot { it.name.startsWith(".") }
  .map { entryFor(it, parentPath) }
  .inWorkspaceOrder()

/**
 * A symlink is described by what it points at, which is how a rootfs has to be read.
 *
 * `File.isDirectory` follows the link already; a broken one is neither a directory nor a
 * readable file and is listed as a file, so a dangling link shows in the browser instead of
 * making the whole directory unopenable.
 */
private fun entryFor(file: File, parentPath: String): WorkspaceEntry = WorkspaceEntry(
  name = file.name,
  path = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}",
  kind = if (file.isDirectory) WorkspaceEntryKind.DIRECTORY else WorkspaceEntryKind.FILE,
  // The file's own mtime, which is what `last_modified` is (`filemanager.py:285`). A broken
  // symlink answers 0 here rather than throwing, and reads as the epoch - which is honest:
  // there is nothing behind it to have a time.
  updatedAt = Instant.ofEpochMilli(file.lastModified()),
)
