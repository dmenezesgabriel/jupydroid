package com.jupydroid.app.ui.spaces

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceServers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the Spaces panel lists and does - vscode's `UserDataProfilesEditorModel`
 * (`vscode@3f3380c src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditorModel.ts`)
 * behind this app's viewmodel seam, over the same [FakeSpaceRepository] `AppContainer` defaults to.
 */
class SpacesViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeSpaceRepository()
  private val servers = FakeSpaceServers()

  private fun ids(model: SpacesViewModel) = model.uiState.value.spaces.map { it.id }

  @Test
  fun aPanelListsBaseOnOpen() = runTest {
    val model = SpacesViewModel(repository, servers)
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id), ids(model))
  }

  @Test
  fun createListsTheNewSpaceAfterBase() = runTest {
    val model = SpacesViewModel(repository, servers)

    model.create("Thesis")
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id, "space-1"), ids(model))
    assertEquals("Thesis", model.uiState.value.spaces.last().name)
  }

  /** The id is the directory and keeps it for life (ADR-0010); only the row's name moves. */
  @Test
  fun renameChangesTheNameAndKeepsTheId() = runTest {
    val model = SpacesViewModel(repository, servers)
    model.create("Thesis")
    advanceUntilIdle()

    model.rename("space-1", "Dissertation")
    advanceUntilIdle()

    assertEquals("space-1" to "Dissertation", model.uiState.value.spaces.last().let { it.id to it.name })
  }

  @Test
  fun deleteRemovesTheRow() = runTest {
    val model = SpacesViewModel(repository, servers)
    model.create("Thesis")
    advanceUntilIdle()

    model.delete("space-1")
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id), ids(model))
  }

  @Test
  fun busyHoldsWhileAnActRunsAndClearsWhenItLands() = runTest {
    val gated = GatedSpaceRepository(repository)
    val model = SpacesViewModel(gated, servers)
    advanceUntilIdle()

    model.create("Thesis")
    advanceUntilIdle()
    assertTrue(model.uiState.value.busy)

    gated.release.complete(Unit)
    advanceUntilIdle()
    assertFalse(model.uiState.value.busy)
  }

  /**
   * The port hands the create's copy fraction up; the panel shows it for the copy's whole length
   * and drops it with `busy` when the act lands.
   */
  @Test
  fun aCreateShowsItsCopyFractionWhileInFlight() = runTest {
    val reporting = ReportingSpaceRepository(repository)
    val model = SpacesViewModel(reporting, servers)
    advanceUntilIdle()

    model.create("Thesis")
    advanceUntilIdle()
    assertEquals(0.5f, model.uiState.value.progress)
    assertTrue(model.uiState.value.busy)

    reporting.release.complete(Unit)
    advanceUntilIdle()
    assertNull(model.uiState.value.progress)
    assertEquals(listOf(BASE_SPACE.id, "space-1"), ids(model))
  }

  /** Two taps in one frame make one Space: an act does not start while another is in flight. */
  @Test
  fun anActIsRefusedWhileAnotherIsInFlight() = runTest {
    val gated = GatedSpaceRepository(repository)
    val model = SpacesViewModel(gated, servers)
    advanceUntilIdle()

    model.create("Thesis")
    model.create("Thesis")
    gated.release.complete(Unit)
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id, "space-1"), ids(model))
  }

  /** The screen declines to draw delete on base; the port still refuses it, and says why. */
  @Test
  fun aRefusedDeleteSurfacesThePortsMessage() = runTest {
    val model = SpacesViewModel(repository, servers)

    model.delete(BASE_SPACE.id)
    advanceUntilIdle()

    assertTrue(model.uiState.value.failure!!.contains("cannot delete the bundled base Space"))
    assertEquals(listOf(BASE_SPACE.id), ids(model))
    assertFalse(model.uiState.value.busy)
  }

  /** A failed list keeps the rows it would have replaced, and says why it failed. */
  @Test
  fun aFailedListKeepsTheLastGoodRows() = runTest {
    val refusing = RefusingSpaceRepository(repository)
    val model = SpacesViewModel(refusing, servers)
    advanceUntilIdle()

    refusing.refusingList = true
    model.create("Thesis")
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id), ids(model))
    assertEquals(RefusingSpaceRepository.REFUSAL, model.uiState.value.failure)
  }

  @Test
  fun theNextActClearsThePreviousFailure() = runTest {
    val model = SpacesViewModel(repository, servers)
    model.delete(BASE_SPACE.id)
    advanceUntilIdle()

    model.create("Thesis")
    advanceUntilIdle()

    assertNull(model.uiState.value.failure)
  }

  @Test
  fun runMarksTheSpaceRunning() = runTest {
    val model = SpacesViewModel(repository, servers)

    model.run(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(setOf(BASE_SPACE.id), model.uiState.value.running)
  }

  @Test
  fun stopMarksTheSpaceStopped() = runTest {
    val model = SpacesViewModel(repository, servers)
    model.run(BASE_SPACE.id)
    advanceUntilIdle()

    model.stop(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(emptySet<String>(), model.uiState.value.running)
  }

  /**
   * The rows follow the port's processes, not the panel's own calls: a cell's lazy start and an
   * idle server's self-stop both reach the home without a tap on it.
   */
  @Test
  fun theRowsFollowAServerThatStartsAndStopsWithoutThePanel() = runTest {
    val model = SpacesViewModel(repository, servers)
    advanceUntilIdle()

    servers.run(BASE_SPACE.id)
    advanceUntilIdle()
    assertEquals(setOf(BASE_SPACE.id), model.uiState.value.running)

    servers.stop(BASE_SPACE.id)
    advanceUntilIdle()
    assertEquals(emptySet<String>(), model.uiState.value.running)
  }

  /** A Space's files are never deleted from under its own live server. */
  @Test
  fun deleteStopsARunningSpaceBeforeRemovingIt() = runTest {
    val witness = DeleteWitnessSpaceRepository(repository, servers)
    val model = SpacesViewModel(witness, servers)
    model.create("Thesis")
    advanceUntilIdle()
    model.run("space-1")
    advanceUntilIdle()

    model.delete("space-1")
    advanceUntilIdle()

    assertEquals(emptySet<String>(), witness.runningAtDelete)
    assertEquals(listOf(BASE_SPACE.id), ids(model))
  }

  /** A server that outlives its Stop keeps its Space: the delete is refused and says which. */
  @Test
  fun aDeleteIsRefusedWhileTheServerOutlivesItsStop() = runTest {
    val outliving = OutlivingSpaceServers(servers)
    val model = SpacesViewModel(repository, outliving)
    model.create("Thesis")
    advanceUntilIdle()
    model.run("space-1")
    advanceUntilIdle()

    model.delete("space-1")
    advanceUntilIdle()

    assertTrue(model.uiState.value.failure!!.contains("space-1"))
    assertEquals(listOf(BASE_SPACE.id, "space-1"), ids(model))
  }
}

/** Holds every `create` until [release] completes, so a test can look at the panel mid-act. */
private class GatedSpaceRepository(private val fake: SpaceRepository) : SpaceRepository by fake {
  val release = CompletableDeferred<Unit>()

  override suspend fun create(name: String, onProgress: suspend (Float) -> Unit): SpaceConfig {
    release.await()
    return fake.create(name, onProgress)
  }
}

/** Reports one mid-copy fraction, then holds until [release] - a create caught mid-copy. */
private class ReportingSpaceRepository(private val fake: SpaceRepository) : SpaceRepository by fake {
  val release = CompletableDeferred<Unit>()

  override suspend fun create(name: String, onProgress: suspend (Float) -> Unit): SpaceConfig {
    onProgress(0.5f)
    release.await()
    return fake.create(name, onProgress)
  }
}

/** A disk that stops answering `spaces()` once [refusingList] is set. */
private class RefusingSpaceRepository(private val fake: SpaceRepository) : SpaceRepository by fake {
  var refusingList = false

  override suspend fun spaces(): List<SpaceConfig> {
    check(!refusingList) { REFUSAL }
    return fake.spaces()
  }

  companion object {
    const val REFUSAL = "could not list 'files/spaces'"
  }
}

/** Records which Spaces were running at the moment `delete` reached the disk. */
private class DeleteWitnessSpaceRepository(private val fake: SpaceRepository, private val servers: SpaceServers) :
  SpaceRepository by fake {
  var runningAtDelete: Set<String>? = null

  override suspend fun delete(id: String) {
    runningAtDelete = servers.running.value
    fake.delete(id)
  }
}

/** A server that outlives its `stop`, as `JupyterServerLauncher` keeps one past its wait. */
private class OutlivingSpaceServers(private val fake: SpaceServers) : SpaceServers by fake {
  override suspend fun stop(spaceId: String) = Unit
}
