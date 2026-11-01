@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.domain.model

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSessions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A deleted file's kernel session ends with it - `DocumentManager.deleteFile` asks
 * `sessions.stopIfNeeded(path)` (jupyterlab@7f0e6906 `packages/docmanager/src/manager.ts`), so the
 * Running panel never lists a kernel for a file that is gone.
 */
class DeletedDocumentSessionsTest {
  private val registry = KernelSessionRegistry()
  private val gateway = FakeKernelGateway(registry)
  private val kernelSessions = FakeKernelSessions(registry)
  private val changes = MutableSharedFlow<WorkspaceChange>()

  @Test
  fun aDeletedNotebooksSessionIsShutDown() = runTest {
    gateway.connect(BASE_SPACE, "a.ipynb")
    watching(kernelSessions)

    changes.emit(WorkspaceChange.Deleted("a.ipynb"))
    runCurrent()

    assertEquals(emptyList<String>(), runningPaths())
  }

  @Test
  fun aSessionAtAnotherPathKeepsRunning() = runTest {
    gateway.connect(BASE_SPACE, "a.ipynb")
    gateway.connect(BASE_SPACE, "f/a.ipynb")
    watching(kernelSessions)

    changes.emit(WorkspaceChange.Deleted("a.ipynb"))
    runCurrent()

    assertEquals(listOf("f/a.ipynb"), runningPaths())
  }

  /** A rename is the session's to follow, not its end - `NotebookSessionRenameTest` owns that half. */
  @Test
  fun aRenamedNotebooksSessionKeepsRunning() = runTest {
    gateway.connect(BASE_SPACE, "a.ipynb")
    watching(kernelSessions)

    changes.emit(WorkspaceChange.Renamed(from = "a.ipynb", to = "b.ipynb"))
    runCurrent()

    assertEquals(listOf("a.ipynb"), runningPaths())
  }

  /** `stopIfNeeded` shuts down only when `matches.length === 1`: two holders of a path, neither is stopped. */
  @Test
  fun twoSessionsAtTheDeletedPathBothKeepRunning() = runTest {
    gateway.connect(BASE_SPACE, "a.ipynb")
    gateway.connect(BASE_SPACE, "a.ipynb")
    watching(kernelSessions)

    changes.emit(WorkspaceChange.Deleted("a.ipynb"))
    runCurrent()

    assertEquals(listOf("a.ipynb", "a.ipynb"), runningPaths())
  }

  /** "Always succeed": one refused listing must not be the last deletion the watch ever serves. */
  @Test
  fun aRefusedListingLeavesTheNextDeletionServed() = runTest {
    gateway.connect(BASE_SPACE, "a.ipynb")
    gateway.connect(BASE_SPACE, "b.ipynb")
    watching(RefusingFirstListingKernelSessions(kernelSessions))

    changes.emit(WorkspaceChange.Deleted("a.ipynb"))
    changes.emit(WorkspaceChange.Deleted("b.ipynb"))
    runCurrent()

    assertEquals(listOf("a.ipynb"), runningPaths())
  }

  /**
   * The workspace's `emit` waits for every listener to take a change, so a listener that held one
   * for the length of a kernel's shutdown would hold an overwrite between its `Deleted` and its
   * `Renamed` - and the open document's adoption of its new path with it.
   */
  @Test
  fun aShutdownStillInFlightDoesNotHoldTheNextAnnouncement() = runTest {
    gateway.connect(BASE_SPACE, "a.ipynb")
    watching(NeverFinishingShutdownKernelSessions(kernelSessions))
    changes.emit(WorkspaceChange.Deleted("a.ipynb"))
    runCurrent()

    val announced = launch { changes.emit(WorkspaceChange.Renamed(from = "b.ipynb", to = "a.ipynb")) }
    runCurrent()

    assertTrue("the rename was taken while the shutdown was still out", announced.isCompleted)
  }

  private fun TestScope.watching(sessions: KernelSessions) {
    DeletedDocumentSessions(changes, sessions).watchIn(backgroundScope)
    runCurrent()
  }

  private suspend fun runningPaths(): List<String> = kernelSessions.running().map { it.path }

  /** Lists and ends like the fake it wraps, after refusing its first listing the way a dropped GET does. */
  private class RefusingFirstListingKernelSessions(private val listing: KernelSessions) : KernelSessions by listing {
    private var refused = false

    override suspend fun running(): List<KernelSessionModel> {
      if (refused) return listing.running()
      refused = true
      throw IOException("GET api/sessions failed: HTTP 503")
    }
  }

  /** Lists like the fake it wraps, and never answers a shutdown - a kernel that is slow to die. */
  private class NeverFinishingShutdownKernelSessions(listing: KernelSessions) : KernelSessions by listing {
    override suspend fun shutdown(serverSessionId: String) = awaitCancellation()
  }
}
