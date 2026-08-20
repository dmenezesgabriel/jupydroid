package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.withMostRecent
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Recents held in memory, for callers with no directory to offer.
 *
 * The container's standing pattern - every port has a fake, so a screen composes before its
 * adapter is wired (`FakeKernelGateway`, `FakeWorkspaceRepository`). It applies the same
 * [withMostRecent] rule the disk store does, because a fake that ordered differently would let a
 * screen test pass against behaviour the real store does not have.
 *
 * ```
 * FakeRecentDocumentsRepository(listOf(RecentDocument("notes.ipynb", WorkspaceEntryKind.FILE)))
 * ```
 */
class FakeRecentDocumentsRepository(initial: List<RecentDocument> = emptyList()) : RecentDocumentsRepository {
  private val recents = MutableStateFlow(initial)

  override fun observeRecents(): Flow<List<RecentDocument>> = recents

  override suspend fun record(document: RecentDocument) {
    recents.value = recents.value.withMostRecent(document)
  }

  override suspend fun clear() {
    recents.value = emptyList()
  }

  /** Nothing here has a filesystem behind it, so nothing can have gone. */
  override suspend fun revalidate() = Unit
}
