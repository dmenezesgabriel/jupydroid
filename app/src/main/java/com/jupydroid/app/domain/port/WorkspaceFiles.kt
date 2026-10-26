package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.nameBeforeCopies
import kotlinx.coroutines.flow.Flow

/**
 * What the user does *to* the shared workspace tree rather than what they read or write in it:
 * create, rename, overwrite and delete, by the same workspace-relative paths [WorkspaceRepository]
 * reads (`""` is the root, nothing above it is addressable).
 *
 * A port of its own because these are the acts that move a path out from under whatever is open on
 * it, which is why [changes] lives here beside them - JupyterLab's `Contents.IDrive` keeps the same
 * pair, the acts and the `fileChanged` signal they raise (jupyterlab@7f0e690
 * `packages/services/src/contents/index.ts`).
 * A reader of the tree needs none of it, and a document needs only [changes].
 *
 * ```
 * workspaceFiles.rename("reports/summary.ipynb", "final.ipynb")   // "reports/final.ipynb"
 * ```
 */
interface WorkspaceFiles {
  /**
   * Every path these acts move or remove, as it happens.
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
   * Writes [text] to a file at [path], with any folders above it that are missing, unless something
   * is already there; answers whether it wrote.
   *
   * For a file whose name is fixed by what reads it, as `.jupyter/mcp_settings.json` is by
   * jupyter-ai. VS Code creates its `mcp.json` the same way: only when it is missing, and with its
   * folders, since `writeFile` makes the parent before it writes (vscode@3f3380c
   * `src/vs/platform/files/common/fileService.ts`, `src/vs/workbench/contrib/mcp/browser/mcpCommands.ts`
   * `OpenMcpResourceCommand`). Nothing is announced on [changes]: nothing can be open on a path that
   * did not exist.
   *
   * ```
   * workspaceFiles.createIfAbsent(".jupyter/mcp_settings.json", "{\"mcp_servers\": []}")   // true, then false
   * ```
   */
  suspend fun createIfAbsent(path: String, text: String): Boolean

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
   * reference's division of labour: `renameFile` catches the 409 and calls `overwrite` only if the
   * dialog is accepted (`docmanager/src/dialogs.ts`). Ours is moved aside, the target deleted,
   * then ours moved into place (`DocumentManager.overwrite`, `manager.ts:514-528`): deleting
   * first would open a window in which neither file exists.
   *
   * The target's removal is announced on [changes] as a delete *before* the rename, in the disk's
   * own order - a document open on it stops writing before the winner's path is adopted.
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

  /**
   * Copies the file at [path] beside itself and answers with the copy's path.
   *
   * `Contents.copy(path, dirname)`, which is all `DocumentManager.duplicate` sends
   * (jupyterlab@7f0e690 `packages/docmanager/src/manager.ts`). The *server* names the copy:
   * any earlier `-Copy<n>` taken out, then counted with `insert="-Copy"` past what the folder
   * holds, so `other.py` and `other-Copy1.py` both give the next free `other-CopyN.py` (`copy`,
   * `services/contents/manager.py`; [nameBeforeCopies]).
   *
   * What is copied is the file as saved - the server's `get` + `save` - so an open document's
   * unsaved edit stays behind, and the copy has no checkpoint of its own. Nothing is announced on
   * [changes]: nothing can be open on a path that did not exist, and a listing picks the copy up
   * when it next reads the folder.
   *
   * A folder is refused, as the server's `400 Can't copy directories` refuses it; so is a path
   * that names nothing or leaves the root.
   *
   * ```
   * repository.copy("reports/other.py")   // "reports/other-Copy1.py"
   * ```
   */
  suspend fun copy(path: String): String
}
