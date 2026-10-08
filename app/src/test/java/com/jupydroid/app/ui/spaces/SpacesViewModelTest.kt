package com.jupydroid.app.ui.spaces

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs
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
  private val environments = FakeSpaceEnvironments(mapOf(BASE_SPACE.id to listOf(STATS)))
  private val kernelSpecs = ReadCountingKernelSpecs(
    FakeKernelSpecs(KernelSpecModels(default = "python3", specs = listOf(PYTHON3_SPEC, STATS_SPEC))),
  )

  private fun ids(model: SpacesViewModel) = model.uiState.value.spaces.map { it.id }

  /** Settings reads a stopped Space's disk, so listing is not an act and never holds the panel. */
  @Test
  fun openSettingsShowsTheSpacesOwnWithoutHoldingThePanel() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    advanceUntilIdle()

    model.openSpaceSettings(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(listOf(STATS), model.uiState.value.environments)
    assertFalse(model.uiState.value.busy)
  }

  /** A refused read says why, and leaves no list behind that belonged to another Space. */
  /** Settings shows what the disk now says, so a registered env reads as registered at once. */
  @Test
  fun registerEnvironmentRelistsTheSpacesEnvironmentsShowingItRegistered() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.openSpaceSettings(BASE_SPACE.id)
    advanceUntilIdle()

    model.registerEnvironment(BASE_SPACE.id, STATS.path)
    advanceUntilIdle()

    assertEquals(listOf(STATS.copy(registeredAs = "stats")), model.uiState.value.environments)
    assertFalse(model.uiState.value.busy)
  }

  /** The default kernel is picked among what the stopped Space's disk lists, so Settings reads them. */
  @Test
  fun openSettingsShowsTheSpacesKernelChoices() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)

    model.openSpaceSettings(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(listOf(PYTHON3_SPEC, STATS_SPEC), model.uiState.value.kernelChoices)
  }

  /** A registration adds a spec, so the choices are read again and the new kernel can be the default. */
  @Test
  fun registerEnvironmentRereadsTheKernelChoices() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.openSpaceSettings(BASE_SPACE.id)
    advanceUntilIdle()

    model.registerEnvironment(BASE_SPACE.id, STATS.path)
    advanceUntilIdle()

    assertEquals(2, kernelSpecs.reads)
  }

  @Test
  fun aRefusedRegistrationIsReportedInThePortsWords() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)

    model.registerEnvironment(BASE_SPACE.id, "envs/nowhere")
    advanceUntilIdle()

    assertTrue(model.uiState.value.failure.orEmpty().contains("envs/nowhere"))
  }

  /** Settings shows the refusal of its own acts, so it opens on none left from an earlier one. */
  @Test
  fun openSettingsClearsAnEarlierRefusal() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.registerEnvironment(BASE_SPACE.id, "envs/nowhere")
    advanceUntilIdle()

    model.openSpaceSettings(BASE_SPACE.id)
    advanceUntilIdle()

    assertNull(model.uiState.value.failure)
  }

  @Test
  fun aRefusedEnvironmentListingIsReportedAndShowsNone() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.openSpaceSettings(BASE_SPACE.id)
    advanceUntilIdle()

    model.openSpaceSettings("space-404")
    advanceUntilIdle()

    assertNull(model.uiState.value.environments)
    assertTrue(model.uiState.value.failure.orEmpty().contains("space-404"))
  }

  @Test
  fun aPanelListsBaseOnOpen() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id), ids(model))
  }

  @Test
  fun createListsTheNewSpaceAfterBase() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)

    model.create("Thesis")
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id, "space-1"), ids(model))
    assertEquals("Thesis", model.uiState.value.spaces.last().name)
  }

  /** The id is the directory and keeps it for life (ADR-0010); only the row's name moves. */
  @Test
  fun renameChangesTheNameAndKeepsTheId() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.create("Thesis")
    advanceUntilIdle()

    model.rename("space-1", "Dissertation")
    advanceUntilIdle()

    assertEquals("space-1" to "Dissertation", model.uiState.value.spaces.last().let { it.id to it.name })
  }

  @Test
  fun deleteRemovesTheRow() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.create("Thesis")
    advanceUntilIdle()

    model.delete("space-1")
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id), ids(model))
  }

  @Test
  fun busyHoldsWhileAnActRunsAndClearsWhenItLands() = runTest {
    val gated = GatedSpaceRepository(repository)
    val model = SpacesViewModel(gated, servers, environments, kernelSpecs)
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
    val model = SpacesViewModel(reporting, servers, environments, kernelSpecs)
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
    val model = SpacesViewModel(gated, servers, environments, kernelSpecs)
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
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)

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
    val model = SpacesViewModel(refusing, servers, environments, kernelSpecs)
    advanceUntilIdle()

    refusing.refusingList = true
    model.create("Thesis")
    advanceUntilIdle()

    assertEquals(listOf(BASE_SPACE.id), ids(model))
    assertEquals(RefusingSpaceRepository.REFUSAL, model.uiState.value.failure)
  }

  @Test
  fun theNextActClearsThePreviousFailure() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.delete(BASE_SPACE.id)
    advanceUntilIdle()

    model.create("Thesis")
    advanceUntilIdle()

    assertNull(model.uiState.value.failure)
  }

  @Test
  fun runMarksTheSpaceRunning() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)

    model.run(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(setOf(BASE_SPACE.id), model.uiState.value.running)
  }

  @Test
  fun stopMarksTheSpaceStopped() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
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
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
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
    val model = SpacesViewModel(witness, servers, environments, kernelSpecs)
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
    val model = SpacesViewModel(repository, outliving, environments, kernelSpecs)
    model.create("Thesis")
    advanceUntilIdle()
    model.run("space-1")
    advanceUntilIdle()

    model.delete("space-1")
    advanceUntilIdle()

    assertTrue(model.uiState.value.failure!!.contains("space-1"))
    assertEquals(listOf(BASE_SPACE.id, "space-1"), ids(model))
  }

  @Test
  fun configureStoresTheSettingsAndTheRowCarriesThem() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.create("Thesis")
    advanceUntilIdle()

    model.configure("space-1", SpaceSettings(idleShutdownMinutes = 2))
    advanceUntilIdle()

    assertEquals(2, model.uiState.value.spaces.last().settings.idleShutdownMinutes)
    assertEquals(2, repository.spaces().last().settings.idleShutdownMinutes)
  }

  /**
   * Nothing is applied to a running process, so a running Space's settings are refused rather
   * than saved for a start the person did not ask for. The message names the Space.
   */
  @Test
  fun configuringARunningSpaceIsRefusedAndWritesNothing() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    model.create("Thesis")
    advanceUntilIdle()
    model.run("space-1")
    advanceUntilIdle()

    model.configure("space-1", SpaceSettings(idleShutdownMinutes = 2))
    advanceUntilIdle()

    assertTrue(model.uiState.value.failure.toString(), model.uiState.value.failure!!.contains("space-1"))
    assertEquals(SpaceSettings(), repository.spaces().last().settings)
  }

  /** The port's own refusal of an out-of-range value reaches the panel as the port worded it. */
  @Test
  fun anOutOfRangeIdleShutdownSurfacesThePortsMessage() = runTest {
    val model = SpacesViewModel(repository, servers, environments, kernelSpecs)
    advanceUntilIdle()

    model.configure(BASE_SPACE.id, SpaceSettings(idleShutdownMinutes = -1))
    advanceUntilIdle()

    assertTrue(model.uiState.value.failure.toString(), model.uiState.value.failure!!.contains("-1"))
  }
}

/** Answers as [fake] does, counting how often the catalogue was read. */
private class ReadCountingKernelSpecs(private val fake: KernelSpecs) : KernelSpecs {
  var reads = 0

  override suspend fun specs(space: Space): KernelSpecModels = fake.specs(space).also { reads++ }
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

private val STATS = PythonEnvironment("envs/stats", hasIpykernel = true)
private val PYTHON3_SPEC = KernelSpec("python3", "Python 3", "python")
private val STATS_SPEC = KernelSpec("stats", "Python (stats)", "python")

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
