package com.jupydroid.app.ui.spaces

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.KernelSpecModels
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Why a Space's row says its default kernel fell back, read off the same disk its launch reads. */
class SpacesViewModelDefaultKernelNoteTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeSpaceRepository()
  private val servers = FakeSpaceServers()
  private val environments = FakeSpaceEnvironments(mapOf(BASE_SPACE.id to listOf(GONE_STATS)))

  /** `rm -r ~/envs/stats` after it was made the default: the row says so, and names the environment. */
  @Test
  fun aDefaultWhoseEnvironmentIsGoneIsNotedOnItsRow() = runTest {
    repository.configure(BASE_SPACE.id, SpaceSettings(defaultKernel = "stats"))

    val model = SpacesViewModel(repository, servers, environments, catalogue("python3", PYTHON3_SPEC))
    advanceUntilIdle()

    assertEquals(
      mapOf(BASE_SPACE.id to "Default kernel is python3, not stats: its environment ~/envs/stats is gone"),
      model.uiState.value.defaultKernelNotes,
    )
  }

  @Test
  fun aDefaultTheCatalogueListsIsNotNoted() = runTest {
    repository.configure(BASE_SPACE.id, SpaceSettings(defaultKernel = "stats"))

    val model = SpacesViewModel(repository, servers, environments, catalogue("stats", PYTHON3_SPEC, STATS_SPEC))
    advanceUntilIdle()

    assertEquals(emptyMap<String, String>(), model.uiState.value.defaultKernelNotes)
  }

  /** Every act re-lists, so a default saved in Settings is checked before the row is drawn again. */
  @Test
  fun aSavedDefaultIsCheckedWhenTheRowsAreListedAgain() = runTest {
    val model = SpacesViewModel(repository, servers, environments, catalogue("python3", PYTHON3_SPEC))
    advanceUntilIdle()

    model.configure(BASE_SPACE.id, SpaceSettings(defaultKernel = "stats"))
    advanceUntilIdle()

    assertEquals(setOf(BASE_SPACE.id), model.uiState.value.defaultKernelNotes.keys)
  }

  /** A stopped Space's catalogue whose effective [default] is already checked against [specs]. */
  private fun catalogue(default: String, vararg specs: KernelSpec) =
    FakeKernelSpecs(KernelSpecModels(default = default, specs = specs.toList()))
}

private val GONE_STATS = PythonEnvironment("envs/stats", hasIpykernel = false, registeredAs = "stats", isMissing = true)
private val PYTHON3_SPEC = KernelSpec("python3", "Python 3", "python")
private val STATS_SPEC = KernelSpec("stats", "Python (stats)", "python")
