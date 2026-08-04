package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.Environment
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContainerTest {
  private val environment = Environment(
    id = "base",
    name = "Base",
    kernels = listOf(KernelSpec(id = "python3", displayName = "Python 3")),
  )

  @Test
  fun container_wiresPorts() = runTest {
    val container = AppContainer(notebookRepository = FakeNotebookRepository())

    assertTrue(!container.serverLauncher.isRunning())
    val connection = container.serverLauncher.start(environment)
    assertEquals("http://127.0.0.1:8888", connection.baseUrl)
    assertTrue(container.serverLauncher.isRunning())

    val session = container.kernelGateway.connect(environment)
    assertEquals("base", session.environmentId)
  }

  @Test
  fun fakeNotebookRepository_roundTrips() = runTest {
    val repository = FakeNotebookRepository()

    val created = repository.createNotebook(title = "Untitled", kernelId = "python3")
    assertEquals("Untitled", repository.readNotebook(created.id).title)

    repository.renameNotebook(created.id, "Renamed")
    assertEquals("Renamed", repository.readNotebook(created.id).title)

    val summaries = repository.observeNotebooks().first()
    assertEquals(listOf("Renamed"), summaries.map { it.title })
  }

  @Test
  fun fakeKernelGateway_executeEmitsIdle() = runTest {
    val gateway = FakeKernelGateway()
    val session = KernelSession(id = "s1", environmentId = "base", kernelId = "python3")

    val events = gateway.execute(session, cellId = "cell-1", code = "1 + 1").toList()
    assertTrue(events.last() is KernelEvent.Idle)
  }
}
