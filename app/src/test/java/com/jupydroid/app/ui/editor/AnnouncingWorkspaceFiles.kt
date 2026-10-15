package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.port.WorkspaceFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A workspace that moves nothing and can say something moved.
 *
 * The editor only ever *listens* to this port - a rename it did not make is announced by the
 * contents layer, the way `Contents.fileChanged` is - so a double that can announce is the whole
 * of what these tests need.
 */
internal class AnnouncingWorkspaceFiles : WorkspaceFiles by FakeWorkspaceFiles() {
  private val _changes = MutableSharedFlow<WorkspaceChange>()
  override val changes: Flow<WorkspaceChange> = _changes

  /** Emits the way `DiskWorkspaceFiles` does, so a test drives the collector and not a setter. */
  suspend fun announce(from: String, to: String) = announce(WorkspaceChange.Renamed(from, to))

  suspend fun announce(change: WorkspaceChange) = _changes.emit(change)
}
