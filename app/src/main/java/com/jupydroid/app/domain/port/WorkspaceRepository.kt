package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.WorkspaceEntry
import kotlinx.coroutines.flow.Flow

/**
 * Reads the directory the kernel, the server and the app all share.
 *
 * Since X1a that is one tree - the kernel's `HOME`, the server's `--ServerApp.root_dir` and the
 * notebook repository's root - so a notebook can `open('data.csv')` on a file the user can see.
 * This is what lets the user see it back.
 *
 * Paths are **relative to the workspace root** throughout, which is `Contents.IModel.path` in
 * JupyterLab: `""` is the root, `reports/summary.ipynb` is a notebook one level down. Nothing above
 * the root is addressable - `list("../..")` is refused, not served, the way jupyter_server's
 * `_validate_path` refuses anything resolving outside `root_dir`.
 *
 * ```
 * repository.list("reports")   // entries, directories first, natural name order
 * ```
 */
interface WorkspaceRepository {
  /**
   * Every path this repository moves or removes, as it happens.
   *
   * `Contents.fileChanged`, which is how an open document in the reference learns that its file
   * went somewhere - it subscribes to the contents layer rather than being told by whichever
   * screen did the renaming (`docregistry/src/context.ts:93`). A document that kept a stale path
   * would write itself back under the old name at the next save, and one whose file was deleted
   * would put it back.
   *
   * Nothing is replayed: a document that opens after a change opens at the path it asked for, and
   * there is nobody to notify when nothing is open.
   */
  val changes: Flow<WorkspaceChange>

  /**
   * What is in [path], already in the order the browser shows it.
   *
   * Ordered here rather than by the caller because the order is part of the behaviour, not a
   * presentation choice: `Private.sort` puts directories first and compares names naturally.
   * Hidden entries are left out, as `ContentsManager.allow_hidden` (false by default) leaves them
   * out of the reference.
   */
  suspend fun list(path: String): List<WorkspaceEntry>

  /**
   * The whole file at [path] as text.
   *
   * One string rather than a stream: this serves an editor, and `Contents.IModel.content` for a
   * text file is likewise the whole thing (`fileFormat: 'text'` in the type registry). Refuses a
   * path outside the root, and a path that names nothing, the same way [list] does.
   */
  suspend fun readText(path: String): String

  /**
   * Replaces the file at [path] with [text], creating it when it is not there.
   *
   * Whole-file too, because that is what `contents.save` sends. The parent directory must already
   * exist - creating one is a separate act with its own name, as `newUntitled(type: 'directory')`
   * is in the reference.
   */
  suspend fun writeText(path: String, text: String)

  /**
   * The whole file at [path] as bytes, for content this app did not author as text.
   *
   * Beside [readText] rather than under it: `Contents.IModel` types content by the file's own
   * format - `'text'` or `'base64'` - and reading a binary file as a string is how a zip becomes a
   * corrupt zip (`ContentsManager.get(format=...)`, `filemanager.py`).
   */
  suspend fun readBytes(path: String): ByteArray

  /**
   * Replaces the file at [path] with [bytes], creating it when it is not there.
   *
   * The byte form of [writeText], and P2j is why it exists: an nbconvert export of a notebook
   * with an image output comes back as a **zip** (`evidence/p2j-2-export-formats/`), and the
   * shape depends on the format *and* on the notebook, so nothing that writes an export may
   * assume text.
   */
  suspend fun writeBytes(path: String, bytes: ByteArray)

  /**
   * Creates an empty file in [directory] and answers with its path.
   *
   * `newUntitled({path, type: "file", ext})`, whose name the *server* supplies: `untitled` plus
   * the extension, incremented past whatever is already there
   * (`services/contents/manager.py:608-613`). So the caller chooses a type, never a name - naming
   * comes after, from the rename the reference opens on the new item.
   *
   * @param extension without the dot, as `createNewFile({ ext: 'txt' })` passes it.
   */
  suspend fun createFile(directory: String, extension: String): String

  /**
   * Creates a folder in [directory] and answers with its path.
   *
   * `Untitled Folder`, counting with a **space** - the server passes `insert=" "` for a directory
   * and for nothing else (`manager.py:603-604`), so the second one is `Untitled Folder 1`.
   */
  suspend fun createFolder(directory: String): String

  /**
   * Renames whatever is at [path] to [name] and answers with the path it now lives at.
   *
   * `Contents.rename(oldPath, newPath)`: the file **moves**, nothing is re-created, and a directory
   * moves exactly like a file - `rename_file` is `shutil.move` (`filemanager.py:619-647`). [name] is
   * a bare name joined onto the folder [path] names, the way the browser joins onto the directory
   * it is showing (`PathExt.join(this._model.path, newName)`, `listing.ts:2621`); a name carrying a
   * `/` is not a name and is refused.
   *
   * Renaming to the name it already has does nothing, silently, which is the server's own first
   * line (`if new_path == old_path: return`).
   *
   * A symlink is moved **as a link**. The workspace lives inside the rootfs, and both references
   * address a link rather than its target - `os.lstat` and an unresolved `normpath` in
   * jupyter_server, `isSymlink` in Winlator's `FileUtils`.
   *
   * ```
   * repository.rename("reports/summary.ipynb", "final.ipynb")   // "reports/final.ipynb"
   * ```
   *
   * @throws FileNameTakenException when [name] is already taken, and only then - the caller offers
   *   an overwrite for exactly this and rethrows everything else, as `renameFile` branches on 409.
   */
  @Throws(FileNameTakenException::class)
  suspend fun rename(path: String, name: String): String

  /**
   * Renames onto a name already taken, replacing what was there.
   *
   * Offered only after the user has been asked - `"%1" already exists, overwrite?` - which is the
   * reference's division of labour, and the same pair [NotebookRepository] already has. Ours is
   * moved aside, the target deleted, then ours moved into place (`DocumentManager.overwrite`,
   * `manager.ts:514-528`): deleting first would open a window in which neither file exists.
   */
  suspend fun overwrite(path: String, name: String): String

  /**
   * Removes whatever [path] names, permanently.
   *
   * `Contents.delete`, in the branch Android leaves us: `delete_file` sends the entry to the
   * platform's trash by default and only *really* deletes when there is none
   * (`filemanager.py:569-614`). An app's private files have no trash, so this is the real one -
   * which is why the caller asks first, and says "permanently".
   *
   * **A folder with anything in it is refused**, as the server refuses it: `400 "Directory %s not
   * empty"` unless `always_delete_dir`, which is false by default. Winlator's file manager deletes
   * a tree recursively; the server sharing this workspace does not, and it is the server a user's
   * other Jupyter client would be talking to.
   *
   * A symlink is **unlinked**, never followed - the one case where following would destroy a tree
   * nobody named. Winlator guards its own recursion the same way (`FileUtils.delete:114-119`).
   *
   * ```
   * repository.delete("reports/summary.ipynb")
   * ```
   */
  suspend fun delete(path: String)
}
