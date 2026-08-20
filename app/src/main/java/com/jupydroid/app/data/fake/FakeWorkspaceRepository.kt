package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.WorkspaceEntry
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

  /** Nothing moves here, so nothing is ever announced. */
  override val changes: Flow<WorkspaceChange> = emptyFlow()

  override suspend fun list(path: String): List<WorkspaceEntry> = entriesByPath[path].orEmpty()

  override suspend fun readText(path: String): String = readBytes(path).decodeToString()

  override suspend fun writeText(path: String, text: String) {
    files[path] = text.toByteArray()
  }

  override suspend fun readBytes(path: String): ByteArray =
    requireNotNull(files[path]) { "no file at '$path' in the workspace" }

  override suspend fun writeBytes(path: String, bytes: ByteArray) {
    files[path] = bytes
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
