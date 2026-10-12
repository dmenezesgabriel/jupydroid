package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.StampedText
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.port.FileChangedOnDiskException
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A workspace held in memory, for callers with no directory to offer.
 *
 * The container's standing pattern - every port has a fake, so a screen composes before its
 * adapter is wired (`FakeKernelGateway`, `FakeServerLauncher`). Empty by default and listable
 * from a map, so a preview or a test can show rows without touching a disk.
 *
 * ```
 * FakeWorkspaceRepository(mapOf("" to listOf(WorkspaceEntry("notes.txt", "notes.txt", FILE))))
 * ```
 */
class FakeWorkspaceRepository(
  private val entriesByPath: Map<String, List<WorkspaceEntry>> = emptyMap(),
  textByPath: Map<String, String> = emptyMap(),
) : WorkspaceRepository {
  /**
   * One store, because the disk this stands in for has one file per path. Two maps - a text one
   * and a byte one - let a caller write through one and read nothing back through the other.
   */
  private val files = textByPath.mapValuesTo(mutableMapOf()) { it.value.toByteArray() }

  /** Each write's stamp is its ordinal, so two writes never share one and no clock is read. */
  private var writes = 0L
  private val stamps = files.keys.associateWithTo(mutableMapOf()) { FileStamp(modifiedNanos = ++writes, size = 0) }

  /** Nothing moves here, so nothing is ever announced. */
  override val changes: Flow<WorkspaceChange> = emptyFlow()

  override suspend fun list(path: String): List<WorkspaceEntry> = entriesByPath[path].orEmpty()

  override suspend fun readStampedText(path: String): StampedText =
    StampedText(readBytes(path).decodeToString(), stamps.getValue(path))

  /**
   * Refuses what the disk one refuses: a stamp that is no longer the file's, unless what is
   * stored is already what the save would write.
   */
  override suspend fun writeText(path: String, text: String, expected: FileStamp?): FileStamp {
    val bytes = text.toByteArray()
    val found = stamps[path]
    val unchanged = expected == null || found == null || found == expected || files[path].contentEquals(bytes)
    if (!unchanged) throw FileChangedOnDiskException(path, checkNotNull(expected), checkNotNull(found))
    return put(path, bytes)
  }

  override suspend fun readBytes(path: String): ByteArray =
    requireNotNull(files[path]) { "no file at '$path' in the workspace" }

  override suspend fun writeBytes(path: String, bytes: ByteArray) {
    put(path, bytes)
  }

  /** The one way in, so no write can forget to move the stamp. */
  private fun put(path: String, bytes: ByteArray): FileStamp {
    files[path] = bytes
    return FileStamp(modifiedNanos = ++writes, size = 0).also { stamps[path] = it }
  }

  override suspend fun createFile(directory: String, extension: String): String =
    throw UnsupportedOperationException("FakeWorkspaceRepository holds a fixed listing; it creates nothing")

  override suspend fun createFolder(directory: String): String =
    throw UnsupportedOperationException("FakeWorkspaceRepository holds a fixed listing; it creates nothing")

  override suspend fun rename(path: String, name: String): String =
    throw UnsupportedOperationException("FakeWorkspaceRepository holds a fixed listing; it moves nothing")

  override suspend fun overwrite(path: String, name: String): String =
    throw UnsupportedOperationException("FakeWorkspaceRepository holds a fixed listing; it moves nothing")

  override suspend fun delete(path: String) =
    throw UnsupportedOperationException("FakeWorkspaceRepository holds a fixed listing; it deletes nothing")
}
