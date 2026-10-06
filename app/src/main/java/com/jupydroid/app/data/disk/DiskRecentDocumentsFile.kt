@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** The file the list lives in, inside the state directory - never inside a workspace. */
internal const val RECENTS_FILE_NAME = "recent-documents.json"

/**
 * Every Space's recents in one file, each entry tagged with the Space it was opened in.
 *
 * JupyterLab's shape: one `docmanager:recents` store, each entry carrying the `root` it was opened
 * under (`jupyterlab@7f0e690 packages/docmanager/src/recents.ts`, `addRecent`). One instance per
 * process, shared by every Space's [DiskRecentDocumentsRepository]: its [writeMutex] is what stops
 * two Spaces' read-modify-writes dropping each other's entries.
 *
 * ```
 * DiskRecentDocumentsRepository(DiskRecentDocumentsFile(context.filesDir), "space-1", workspace)
 * ```
 */
class DiskRecentDocumentsFile(private val stateDir: File, private val json: Json = Json { ignoreUnknownKeys = true }) {
  /**
   * One write at a time, across every Space. A write the filesystem refuses throws out of
   * [update], so the caller never publishes a list the next launch would not find.
   */
  private val writeMutex = Mutex()

  /**
   * [spaceId]'s entries as stored, unvalidated. A store that cannot be parsed reads as empty: this
   * runs before the first screen draws, and the reference's own `_loadRecents` catches its failure
   * and carries on for the same reason.
   */
  fun read(spaceId: String): List<RecentDocument> = readAll().filter { it.spaceId() == spaceId }.map { it.toDomain() }

  /**
   * Replaces [spaceId]'s entries with [transform] of them, keeping every other Space's as they
   * are, and returns what [spaceId] now holds. A transform that changes nothing writes nothing.
   */
  suspend fun update(
    spaceId: String,
    transform: (List<RecentDocument>) -> List<RecentDocument>,
  ): List<RecentDocument> = writeMutex.withLock {
    val (own, others) = readAll().partition { it.spaceId() == spaceId }
    val stored = own.map { it.toDomain() }
    val updated = transform(stored)
    if (updated != stored) write(others + updated.map { it.stored(spaceId) })
    updated
  }

  private fun readAll(): List<StoredRecent> = try {
    json.decodeFromString<StoredRecents>(storeFile().readText()).documents
  } catch (failure: SerializationException) {
    emptyList()
  } catch (failure: IOException) {
    emptyList()
  }

  private fun write(documents: List<StoredRecent>) {
    stateDir.mkdirs()
    storeFile().writeText(json.encodeToString(StoredRecents(documents)))
  }

  private fun storeFile(): File = File(stateDir, RECENTS_FILE_NAME)
}

/** The stored shape, kept apart from the domain type so the file format can move on its own. */
@Serializable
private data class StoredRecents(val documents: List<StoredRecent> = emptyList())

/** [space] is null on an entry written before Spaces could be entered, and every one of those was base's. */
@Serializable
private data class StoredRecent(val path: String, val directory: Boolean, val space: String? = null)

private fun StoredRecent.spaceId(): String = space ?: BASE_SPACE.id

private fun RecentDocument.stored(spaceId: String) = StoredRecent(path, kind == WorkspaceEntryKind.DIRECTORY, spaceId)

private fun StoredRecent.toDomain() =
  RecentDocument(path, if (directory) WorkspaceEntryKind.DIRECTORY else WorkspaceEntryKind.FILE)
