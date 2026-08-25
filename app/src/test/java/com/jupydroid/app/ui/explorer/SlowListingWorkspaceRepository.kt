package com.jupydroid.app.ui.explorer

import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * A workspace whose listing of one directory arrives late, so two listings can be made to land
 * out of the order they were started in.
 *
 * **Why a fake and not a `StandardTestDispatcher`.** Queued coroutines complete in the order they
 * were launched, which is the one ordering the defect this exists for does *not* need: the
 * clobber happens when a listing started **first** finishes **last**. Only a per-path delay puts
 * the completions in that order on purpose.
 *
 * ```
 * SlowListingWorkspaceRepository(DiskWorkspaceRepository(root, dispatcher), slowPath = "reports")
 * ```
 *
 * @param workspace what actually answers; every call but the slow listing goes straight through.
 * @param slowPath the one directory whose [list] waits before it answers.
 * @param waitMillis how long it waits - virtual time under `runTest`, so it costs nothing.
 */
internal class SlowListingWorkspaceRepository(
  private val workspace: WorkspaceRepository,
  private val slowPath: String,
  private val waitMillis: Long = 1_000,
) : WorkspaceRepository {
  override val changes: Flow<WorkspaceChange> get() = workspace.changes

  override suspend fun list(path: String): List<WorkspaceEntry> {
    if (path == slowPath) delay(waitMillis)
    return workspace.list(path)
  }

  override suspend fun readText(path: String): String = workspace.readText(path)

  override suspend fun writeText(path: String, text: String) = workspace.writeText(path, text)

  override suspend fun readBytes(path: String): ByteArray = workspace.readBytes(path)

  override suspend fun writeBytes(path: String, bytes: ByteArray) = workspace.writeBytes(path, bytes)

  override suspend fun createFile(directory: String, extension: String): String =
    workspace.createFile(directory, extension)

  override suspend fun createFolder(directory: String): String = workspace.createFolder(directory)

  override suspend fun rename(path: String, name: String): String = workspace.rename(path, name)

  override suspend fun overwrite(path: String, name: String): String = workspace.overwrite(path, name)

  override suspend fun delete(path: String) = workspace.delete(path)
}
