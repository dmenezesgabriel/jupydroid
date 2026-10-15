package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.port.WorkspaceFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A workspace whose files never move, for callers with no directory to offer.
 *
 * The partner of [FakeWorkspaceRepository], whose listing is fixed: there is nothing here to
 * create, copy, rename or delete, so every act is refused and nothing is ever announced. A test that
 * needs an act to land overrides it on a named subclass by delegation.
 *
 * ```
 * class DeletingFiles : WorkspaceFiles by FakeWorkspaceFiles() { override suspend fun delete(path: String) { … } }
 * ```
 */
class FakeWorkspaceFiles : WorkspaceFiles {
  override val changes: Flow<WorkspaceChange> = emptyFlow()

  override suspend fun createFile(directory: String, extension: String): String =
    throw UnsupportedOperationException("FakeWorkspaceFiles holds a fixed listing; it creates nothing")

  override suspend fun createFolder(directory: String): String =
    throw UnsupportedOperationException("FakeWorkspaceFiles holds a fixed listing; it creates nothing")

  override suspend fun rename(path: String, name: String): String =
    throw UnsupportedOperationException("FakeWorkspaceFiles holds a fixed listing; it moves nothing")

  override suspend fun overwrite(path: String, name: String): String =
    throw UnsupportedOperationException("FakeWorkspaceFiles holds a fixed listing; it moves nothing")

  override suspend fun delete(path: String) =
    throw UnsupportedOperationException("FakeWorkspaceFiles holds a fixed listing; it deletes nothing")

  override suspend fun copy(path: String): String =
    throw UnsupportedOperationException("FakeWorkspaceFiles holds a fixed listing; it creates nothing")
}
