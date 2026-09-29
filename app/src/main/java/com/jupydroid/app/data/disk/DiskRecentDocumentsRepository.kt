@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.withMostRecent
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/** The file the list lives in, inside [stateDir] - never inside the workspace. */
internal const val RECENTS_FILE_NAME = "recent-documents.json"

/**
 * The recents list, held in [stateDir] and validated against the workspace it points into.
 *
 * **[stateDir] is not the workspace, and that is the design.** The workspace is the kernel's `HOME`
 * and the server's `root_dir`, shared with any other Jupyter client - a bookkeeping file
 * there would show up in the explorer's own listing and in an `ls` from a cell. The reference keeps
 * recents in `IStateDB` under `docmanager:recents` (`recents.ts:288`), which is client state and
 * not a Contents API file, and X1a kept its migration stamp out of the rootfs `home` for the same
 * reason.
 *
 * ```
 * DiskRecentDocumentsRepository(context.filesDir, notebookWorkspace(context))
 * ```
 */
class DiskRecentDocumentsRepository(
  private val stateDir: File,
  workspaceDir: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecentDocumentsRepository {
  private val workspace = WorkspaceRoot(workspaceDir)
  private val recents = MutableStateFlow(loadValidated())

  override fun observeRecents(): Flow<List<RecentDocument>> = recents

  /**
   * A path the workspace cannot address is dropped rather than stored.
   *
   * `fileAt` throws on anything that leaves the root, and a recent that would open something
   * outside the workspace is worse than one that was never recorded - there is no screen that
   * could honour it.
   */
  override suspend fun record(document: RecentDocument) = withContext(ioDispatcher) {
    if (!isInsideTheWorkspace(document)) return@withContext
    write(recents.value.withMostRecent(document))
  }

  override suspend fun clear() = withContext(ioDispatcher) { write(emptyList()) }

  /**
   * The same filter [loadValidated] applies at launch, applied again on demand.
   *
   * Only writes when something actually went, so a refresh on a healthy list costs one `lstat` per
   * entry and touches no disk.
   */
  override suspend fun revalidate() = withContext(ioDispatcher) {
    val surviving = recents.value.filter { stillThere(it) }
    if (surviving.size != recents.value.size) write(surviving)
  }

  private fun write(updated: List<RecentDocument>) {
    recents.value = updated
    stateDir.mkdirs()
    storeFile().writeText(json.encodeToString(StoredRecents(updated.map { it.stored() })))
  }

  /**
   * What is read back at launch, minus anything that has since gone.
   *
   * `_loadRecents` filters every entry through `_isValid` before the list is ever shown
   * (`recents.ts:203-224`), where invalid means the Contents API answered **404**. This workspace
   * is shared with the kernel, so a cell calling `os.remove` is an ordinary way for a recent to go
   * stale rather than an edge case.
   *
   * A store that cannot be parsed reads as empty. This runs before the first screen draws, and the
   * reference's own `_loadRecents` catches its failure and carries on for the same reason.
   */
  private fun loadValidated(): List<RecentDocument> = try {
    json.decodeFromString<StoredRecents>(storeFile().readText())
      .documents
      .map { it.toDomain() }
      .filter { stillThere(it) }
  } catch (failure: SerializationException) {
    emptyList()
  } catch (failure: java.io.IOException) {
    emptyList()
  }

  /** `isPresent`, not `exists`: a dangling symlink is still something the browser lists. */
  private fun stillThere(document: RecentDocument): Boolean =
    isInsideTheWorkspace(document) && workspace.isPresent(workspace.fileAt(document.path))

  private fun isInsideTheWorkspace(document: RecentDocument): Boolean = try {
    workspace.fileAt(document.path)
    true
  } catch (refused: IllegalArgumentException) {
    false
  }

  private fun storeFile(): File = File(stateDir, RECENTS_FILE_NAME)
}

/** The stored shape, kept apart from the domain type so the file format can move on its own. */
@Serializable
private data class StoredRecents(val documents: List<StoredRecent> = emptyList())

@Serializable
private data class StoredRecent(val path: String, val directory: Boolean)

private fun RecentDocument.stored() = StoredRecent(path, kind == WorkspaceEntryKind.DIRECTORY)

private fun StoredRecent.toDomain() =
  RecentDocument(path, if (directory) WorkspaceEntryKind.DIRECTORY else WorkspaceEntryKind.FILE)
