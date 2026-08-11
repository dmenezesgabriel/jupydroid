package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.port.WorkspaceRepository

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
) : WorkspaceRepository {
  override suspend fun list(path: String): List<WorkspaceEntry> = entriesByPath[path].orEmpty()
}
