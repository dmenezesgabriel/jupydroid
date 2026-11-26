@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.environments

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.EnvironmentCreate
import com.jupydroid.app.data.fake.FakeSpaceEnvironmentCreation
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * What the Environments panel's ViewModel asks of [SpaceEnvironments],
 * [SpaceEnvironmentCreation] and the workspace listing: the reads a poll refreshes, the register
 * act, and the one create at a time whose streamed lines, exit and refusals the panel reports.
 */
class EnvironmentsPanelViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val environments = FakeSpaceEnvironments()
  private val creation = FakeSpaceEnvironmentCreation()
  private val workspace = FakeWorkspaceRepository()

  private fun viewModel() = EnvironmentsPanelViewModel(
    spaceId = BASE_SPACE.id,
    spaceEnvironments = environments,
    spaceEnvironmentCreation = creation,
    workspaceRepository = workspace,
  )

  /** Before the first read lands the panel has nothing to draw but "Reading…". */
  @Test
  fun thePanelReadsNothingUntilAsked() {
    val model = viewModel()

    val state = model.uiState.value
    assertNull(state.environments)
    assertNull(state.projects)
    assertNull(state.create)
    assertNull(state.refusal)
  }

  /**
   * A refresh lists the Space's environments and its project folders - dirs that are neither
   * hidden nor env containers.
   */
  @Test
  fun refreshListsTheSpacesEnvironmentsAndProjects() = runTest {
    val model = EnvironmentsPanelViewModel(
      BASE_SPACE.id,
      refreshSeededEnvironments(),
      creation,
      refreshSeededFolders(),
    )

    model.refresh()
    advanceUntilIdle()

    assertEquals(listOf("proj-a/.venv", "envs/stats"), model.uiState.value.environments?.map { it.path })
    assertEquals(listOf("proj-a", "My Project"), model.uiState.value.projects)
  }

  /** A refused read keeps the rows it would have replaced and says why, as the Spaces acts do. */
  @Test
  fun aRefusedReadKeepsTheRowsAndSaysWhy() = runTest {
    val refusing = object : SpaceEnvironments by environments {
      override suspend fun environments(spaceId: String): List<PythonEnvironment> =
        throw IllegalArgumentException("no Space with id '$spaceId'")
    }
    val model = EnvironmentsPanelViewModel(BASE_SPACE.id, refusing, creation, workspace)
    model.refresh()
    advanceUntilIdle()

    assertNull(model.uiState.value.environments)
    assertEquals("no Space with id '${BASE_SPACE.id}'", model.uiState.value.refusal)
  }

  /** Register writes the spec through the port, then re-reads so the row shows the kernel's name. */
  @Test
  fun registerRegistersTheEnvironmentAndReadsAgain() = runTest {
    val seeded = FakeSpaceEnvironments(
      mapOf(BASE_SPACE.id to listOf(PythonEnvironment("proj-a/.venv", hasIpykernel = true))),
    )
    var reads = 0
    val counting = object : SpaceEnvironments by seeded {
      override suspend fun environments(spaceId: String): List<PythonEnvironment> {
        reads++
        return seeded.environments(spaceId)
      }
    }
    val model = EnvironmentsPanelViewModel(BASE_SPACE.id, counting, creation, workspace)
    model.refresh()
    advanceUntilIdle()

    model.register("proj-a/.venv")
    advanceUntilIdle()

    assertEquals("proj-a-.venv", model.uiState.value.environments?.single()?.registeredAs)
    assertEquals(2, reads)
    assertNull(model.uiState.value.refusal)
  }

  /** A refused register says why in the port's words and re-reads nothing. */
  @Test
  fun aRefusedRegisterSaysWhyAndReadsNothing() = runTest {
    val seeded = FakeSpaceEnvironments(
      mapOf(BASE_SPACE.id to listOf(PythonEnvironment("proj-a/.venv", hasIpykernel = false))),
    )
    var reads = 0
    val counting = object : SpaceEnvironments by seeded {
      override suspend fun environments(spaceId: String): List<PythonEnvironment> {
        reads++
        return seeded.environments(spaceId)
      }
    }
    val model = EnvironmentsPanelViewModel(BASE_SPACE.id, counting, creation, workspace)
    model.refresh()
    advanceUntilIdle()

    model.register("proj-a/.venv")
    advanceUntilIdle()

    val refusal = model.uiState.value.refusal
    assertTrue("must say ipykernel, was '$refusal'", refusal?.contains("ipykernel") == true)
    assertEquals(1, reads)
  }

  /** While the script runs, its lines so far and the project it is for are the state's whole story. */
  @Test
  fun createStreamsTheScriptsLinesIntoTheState() = runTest {
    val hanging = HangingSpaceEnvironmentCreation()
    val model = EnvironmentsPanelViewModel(BASE_SPACE.id, environments, hanging, workspace)

    model.create("proj-a")
    advanceUntilIdle()

    assertEquals(
      EnvironmentCreateState.Running("proj-a", listOf("Collecting package metadata")),
      model.uiState.value.create,
    )
    assertEquals(listOf(EnvironmentCreate(BASE_SPACE.id, "proj-a")), hanging.creates)
  }

  /** A clean exit clears the create block and re-reads the environments the script just made. */
  @Test
  fun aCleanCreateClearsItselfAndReadsAgain() = runTest {
    var reads = 0
    val counting = object : SpaceEnvironments by environments {
      override suspend fun environments(spaceId: String): List<PythonEnvironment> {
        reads++
        return environments.environments(spaceId)
      }
    }
    val model = EnvironmentsPanelViewModel(BASE_SPACE.id, counting, creation, workspace)
    model.refresh()
    advanceUntilIdle()
    val readsBefore = reads

    model.create("proj-a")
    advanceUntilIdle()

    assertNull(model.uiState.value.create)
    assertEquals(listOf(EnvironmentCreate(BASE_SPACE.id, "proj-a")), creation.creates)
    assertTrue("a clean create re-reads the environments", reads > readsBefore)
  }

  /**
   * A failed exit keeps the code and the output's tail - a partial `.venv` is the script's own
   * verdict to retry past.
   */
  @Test
  fun aFailedCreateKeepsItsExitAndTheOutputsTail() = runTest {
    creation.eventsFor =
      { listOf(SpaceShellEvent.Output("first line"), SpaceShellEvent.Output("last line"), SpaceShellEvent.Finished(3)) }
    val model = viewModel()

    model.create("proj-a")
    advanceUntilIdle()

    assertEquals(
      EnvironmentCreateState.Failed("proj-a", exitCode = 3, excerpt = "first line\nlast line"),
      model.uiState.value.create,
    )
  }

  /** A run that never produces an exit - the port refused or the flow died - is refused, not failed. */
  @Test
  fun aCreateThatThrowsIsRefused() = runTest {
    creation.eventsFor = { throw IllegalStateException("no proot on this device") }
    val model = viewModel()

    model.create("proj-a")
    advanceUntilIdle()

    assertEquals(
      EnvironmentCreateState.Refused("proj-a", "no proot on this device"),
      model.uiState.value.create,
    )
  }

  /** One create at a time: a second tap while the script runs is refused rather than queued. */
  @Test
  fun aSecondCreateWhileOneRunsIsRefused() = runTest {
    val hanging = HangingSpaceEnvironmentCreation()
    val model = EnvironmentsPanelViewModel(BASE_SPACE.id, environments, hanging, workspace)

    model.create("proj-a")
    advanceUntilIdle()
    model.create("proj-b")
    advanceUntilIdle()

    assertEquals(listOf(EnvironmentCreate(BASE_SPACE.id, "proj-a")), hanging.creates)
    assertEquals(
      EnvironmentCreateState.Running("proj-a", listOf("Collecting package metadata")),
      model.uiState.value.create,
    )
  }

  /** A failed create does not hold the slot: the next project can be tried. */
  @Test
  fun aCreateAfterAFailureIsAllowed() = runTest {
    creation.eventsFor = { listOf(SpaceShellEvent.Finished(1)) }
    val model = viewModel()
    model.create("proj-a")
    advanceUntilIdle()

    model.create("proj-b")
    advanceUntilIdle()

    assertEquals(
      listOf(EnvironmentCreate(BASE_SPACE.id, "proj-a"), EnvironmentCreate(BASE_SPACE.id, "proj-b")),
      creation.creates,
    )
  }

  /** The environments the refresh test seeds: one in a project, one in a container. */
  private fun refreshSeededEnvironments() = FakeSpaceEnvironments(
    mapOf(
      BASE_SPACE.id to listOf(
        PythonEnvironment("proj-a/.venv", hasIpykernel = true),
        PythonEnvironment("envs/stats", hasIpykernel = false),
      ),
    ),
  )

  /** The workspace listing the refresh test seeds: projects, a container, a dot-dir and a file. */
  private fun refreshSeededFolders() = FakeWorkspaceRepository(
    mapOf(
      "" to listOf(
        entry("proj-a"),
        entry("envs"),
        entry(".hidden"),
        entry("notes.txt", WorkspaceEntryKind.FILE),
        entry("My Project"),
      ),
    ),
  )

  private fun entry(name: String, kind: WorkspaceEntryKind = WorkspaceEntryKind.DIRECTORY) =
    WorkspaceEntry(name, name, kind, Instant.EPOCH)
}

/**
 * A creation whose run never ends: the state a panel sits in while pip downloads. Records its
 * asks the way [FakeSpaceEnvironmentCreation] does, cold flow and all.
 */
private class HangingSpaceEnvironmentCreation : SpaceEnvironmentCreation {
  val creates = mutableListOf<EnvironmentCreate>()

  override fun create(spaceId: String, project: String): Flow<SpaceShellEvent> = flow {
    val create = EnvironmentCreate(spaceId, project)
    creates += create
    emit(SpaceShellEvent.Output("Collecting package metadata"))
    awaitCancellation()
  }
}
