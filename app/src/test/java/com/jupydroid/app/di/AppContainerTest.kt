package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.first
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

    val session = container.portsOf(space.id).kernelGateway.connect(space, "Analysis.ipynb")
    assertEquals("base", session.spaceId)
  }

  /** The default wiring hands each Space its own value, so a call site never has to name base. */
  @Test
  fun portsOf_carriesTheSpaceItWasBuiltFor() {
    assertEquals("space-1", AppContainer().portsOf("space-1").space.id)
  }

  /**
   * One registry per Space: a stopped Space's sessions port ends everything its registry holds
   * (`BaseSpaceKernelSessions.running`), so a shared one would put base's open notebooks on
   * No Kernel the moment Thesis's Running panel was looked at.
   */
  @Test
  fun eachSpacesSessionsListOnlyWhatItsOwnGatewayOpened() = runTest {
    val container = AppContainer()
    container.portsOf("base").kernelGateway.connect(space, "Analysis.ipynb")

    assertEquals(emptyList<String>(), container.portsOf("space-1").kernelSessions.running().map { it.path })
    assertEquals(listOf("Analysis.ipynb"), container.portsOf("base").kernelSessions.running().map { it.path })
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

  /**
   * The language server dies with the Spaces, every Space's and not base's alone: Thesis's `pylsp`
   * left behind keeps an interpreter alive for an app nobody is using.
   */
  @Test
  fun leavingTheAppIdle_stopsEverySpacesLanguageServer() = runTest {
    val languageServer = FakeLanguageServerPort()
    val container = AppContainer(languageServer = languageServer, applicationScope = backgroundScope)

    container.idleServerStopper.onAppStopped()
    advanceTimeBy(31.seconds)
    runCurrent()

    assertEquals(1, languageServer.stopAllCalls)
  }

  /**
   * Each Space's shells are its own server's, and every one of them holds the app awake: a shell in
   * Thesis is not in base's list, and base's empty list does not erase it from the count.
   */
  @Test
  fun eachSpacesTerminalsAreItsOwnAndAllKeepTheAppAwake() = runTest {
    val container = AppContainer()
    container.portsOf("space-1").terminalGateway.open(cwd = null)

    assertEquals(emptyList<String>(), container.portsOf("base").terminalGateway.running().map { it.name })
    assertEquals(1, container.activeTerminals.open.first())
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
      enteredSpacePorts = { spaceId, _ ->
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
