package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class AppContainerTest {
  private val space = Space(
    id = "base",
    name = "Base",
    kernels = listOf(KernelSpec(name = "python3", displayName = "Python 3", language = "python")),
  )

  @Test
  fun container_wiresPorts() = runTest {
    val container = AppContainer()

    assertEquals(emptySet<String>(), container.spaceServers.running.value)
    container.spaceServers.run(space.id)
    assertEquals(setOf("base"), container.spaceServers.running.value)

    val session = container.kernelGateway.connect(space, "Analysis.ipynb")
    assertEquals("base", session.spaceId)
  }

  /**
   * Leaving the app idle stops every Space the home ran, not base alone - a second Space's server
   * left behind holds ~124 MB until the app dies (`TwoSpacesAtOnceDeviceTest`).
   */
  @Test
  fun leavingTheAppIdle_stopsEverySpacesServer() = runTest {
    val container = AppContainer(applicationScope = backgroundScope)
    container.spaceServers.run("base")
    container.spaceServers.run("space-1")

    container.idleServerStopper.onAppStopped()
    advanceTimeBy(31.seconds)
    runCurrent()

    assertEquals(emptySet<String>(), container.spaceServers.running.value)
  }

  /** A device with no Space wiring still lists the base Space, as every installed device holds it. */
  @Test
  fun container_defaultsSpaceRepositoryToAFakeHoldingBase() = runTest {
    val container = AppContainer()

    assertEquals(listOf("base"), container.spaceRepository.spaces().map { it.id })
  }

  /**
   * One set per Space for the process's life: a second entry into a Space must reach the same
   * repositories as the first, and two Spaces must never share one.
   */
  @Test
  fun portsOf_buildsEachSpacesSetOnceAndKeepsThemApart() {
    val built = mutableListOf<String>()
    val container = AppContainer(
      enteredSpacePorts = { spaceId ->
        built += spaceId
        EnteredSpacePorts(FakeNotebookRepository())
      },
    )

    val thesis = container.portsOf("space-1")

    assertSame(thesis, container.portsOf("space-1"))
    assertNotSame(thesis, container.portsOf("base"))
    assertEquals(listOf("space-1", "base"), built)
  }

  @Test
  fun fakeNotebookRepository_roundTrips() = runTest {
    val repository = FakeNotebookRepository()

    val created = repository.createNotebook(title = "Untitled", kernelId = "python3")
    assertEquals("Untitled", repository.readNotebook(created.id).title)
  }

  /** The fake must fail the same way the disk adapter does, or callers that catch one break on the other. */
  @Test
  fun fakeNotebookRepository_rejectsUnknownIdLikeTheDiskAdapter() = runTest {
    val repository = FakeNotebookRepository()

    try {
      repository.readNotebook("ghost")
      fail("expected IllegalArgumentException for an unknown notebook id")
    } catch (e: IllegalArgumentException) {
      assertTrue("message must name the offending id, was '${e.message}'", e.message!!.contains("ghost"))
    }
  }

  @Test
  fun fakeKernelGateway_executeEmitsIdle() = runTest {
    val gateway = FakeKernelGateway()
    val session = KernelSession(
      id = "s1",
      spaceId = "base",
      kernelId = "python3",
      kernelName = "python3",
      serverSessionId = "server-s1",
    )

    val events = gateway.execute(session, cellId = "cell-1", code = "1 + 1").toList()
    assertTrue(events.last() is KernelEvent.Idle)
  }
}
