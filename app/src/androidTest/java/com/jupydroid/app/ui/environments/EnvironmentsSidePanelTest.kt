package com.jupydroid.app.ui.environments

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.EnvironmentCreate
import com.jupydroid.app.data.fake.FakeSpaceEnvironmentCreation
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeSpaceKernelRegistration
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.KernelRegister
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.SpaceInterpreter
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceKernelRegistration
import com.jupydroid.app.domain.port.SpaceShellEvent
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.theme.JupydroidTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Environments panel: the Space's environments grouped by the project folder each belongs
 * to, the workspace's own in a trailing group, and the two acts a row or a project offers -
 * Register for an environment made elsewhere, Create for a project's `.venv`. What is pressable
 * and what it reports is this test's question, as `SidePanelBarTest`'s is the bar's.
 */
@RunWith(AndroidJUnit4::class)
class EnvironmentsSidePanelTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var viewModel: EnvironmentsPanelViewModel

  /** The panel's own chrome: its title and the toolbar's refresh, as every side panel's are. */
  @Test
  fun thePanelIsTitledAndOffersRefresh() {
    show()

    compose.onNodeWithText("Environments").assertIsDisplayed()
    compose.onNodeWithContentDescription("Refresh list").assertIsDisplayed()
  }

  /** A workspace holding no environment and no project folder reads the scan's own answer. */
  @Test
  fun aWorkspaceWithNothingReadsNoneFound() {
    show()

    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.environments != null }
    compose.onNodeWithText(NO_ENVIRONMENTS).assertIsDisplayed()
  }

  /** Each project folder is a section; an environment's first path segment decides its group. */
  @Test
  fun eachProjectListsItsEnvironmentsAndTheWorkspaceItsOwn() {
    show(
      spaceEnvironments = FakeSpaceEnvironments(
        mapOf(
          BASE_SPACE.id to listOf(
            PythonEnvironment("proj-a/.venv", hasIpykernel = true, registeredAs = "proj-a-.venv"),
            PythonEnvironment("envs/stats", hasIpykernel = false),
          ),
        ),
      ),
      workspaceRepository = FakeWorkspaceRepository(
        mapOf("" to listOf(entry("proj-a"), entry("proj-b"))),
      ),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.environments != null }

    compose.onNodeWithText("proj-a").assertExists()
    compose.onNodeWithText("proj-b").assertExists()
    compose.onNodeWithText("proj-a/.venv").assertExists()
    compose.onNodeWithText("envs/stats").assertExists()
    compose.onNodeWithText("Workspace").assertExists()
    compose.onNodeWithText(registeredLabel("proj-a-.venv")).assertExists()
    compose.onNodeWithText(HAS_IPYKERNEL).assertExists()
  }

  /** Register is pressable exactly when a kernel could start in the environment - the row's own rule. */
  @Test
  fun registerIsPressableOnlyWithIpykernel() {
    show(
      spaceEnvironments = FakeSpaceEnvironments(
        mapOf(
          BASE_SPACE.id to listOf(
            PythonEnvironment("envs/stats", hasIpykernel = true),
            PythonEnvironment("envs/bare", hasIpykernel = false),
          ),
        ),
      ),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.environments != null }

    compose.onNodeWithContentDescription(registerLabel("envs/stats")).assertIsEnabled()
    compose.onNodeWithContentDescription(registerLabel("envs/bare")).assertIsNotEnabled()
  }

  /** A registered environment's row says its kernel's name instead of offering Register again. */
  @Test
  fun aRegisteredEnvironmentOffersNoRegister() {
    show(
      spaceEnvironments = FakeSpaceEnvironments(
        mapOf(BASE_SPACE.id to listOf(PythonEnvironment("envs/stats", hasIpykernel = true, registeredAs = "stats"))),
      ),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.environments != null }

    compose.onNodeWithText(registeredLabel("stats")).assertExists()
    compose.onNodeWithContentDescription(registerLabel("envs/stats")).assertDoesNotExist()
  }

  /** A registered environment deleted since offers the command that removes its spec, not Register. */
  @Test
  fun aGoneEnvironmentOffersItsRemoveCommand() {
    show(
      spaceEnvironments = FakeSpaceEnvironments(
        mapOf(
          BASE_SPACE.id to listOf(
            PythonEnvironment("envs/stats", hasIpykernel = false, registeredAs = "stats", isMissing = true),
          ),
        ),
      ),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.environments != null }

    compose.onNodeWithText(goneLabel("stats")).assertExists()
    compose.onNodeWithText("jupyter kernelspec remove stats").assertExists()
    compose.onNodeWithContentDescription(registerLabel("envs/stats")).assertDoesNotExist()
  }

  /** Every project folder offers its own create, and a tap asks the port for that folder. */
  @Test
  fun eachProjectOffersCreateForItsOwnFolder() {
    val creation = FakeSpaceEnvironmentCreation()
    show(
      spaceEnvironmentCreation = creation,
      workspaceRepository = FakeWorkspaceRepository(mapOf("" to listOf(entry("proj-a"), entry("proj-b")))),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.projects != null }

    compose.onNodeWithContentDescription(createLabel("proj-a")).performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { creation.creates.isNotEmpty() }
    compose.runOnIdle { assertEquals(listOf(EnvironmentCreate(BASE_SPACE.id, "proj-a")), creation.creates) }
  }

  /** While the script runs the panel says so and streams its lines; a clean exit clears the block. */
  @Test
  fun aCreateStreamsItsProgressWhileItRuns() {
    val creation = GatedSpaceEnvironmentCreation()
    show(
      spaceEnvironmentCreation = creation,
      workspaceRepository = FakeWorkspaceRepository(mapOf("" to listOf(entry("proj-a")))),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.projects != null }
    compose.onNodeWithContentDescription(createLabel("proj-a")).performScrollTo().performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.create is ScriptActState.Running }

    compose.onNodeWithText("Creating environment for proj-a").assertIsDisplayed()
    compose.onNodeWithText("Creating virtual environment").assertIsDisplayed()
    creation.gate.complete(Unit)
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.create == null }

    compose.onNodeWithText("Creating environment for proj-a").assertDoesNotExist()
  }

  /** A failed exit keeps the code and the output's tail on screen - the script's own verdict. */
  @Test
  fun aFailedCreateShowsTheExitAndTheOutputsTail() {
    val creation = FakeSpaceEnvironmentCreation()
    creation.eventsFor = {
      listOf(SpaceShellEvent.Output("pip: resolution impossible"), SpaceShellEvent.Finished(4))
    }
    show(
      spaceEnvironmentCreation = creation,
      workspaceRepository = FakeWorkspaceRepository(mapOf("" to listOf(entry("proj-a")))),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.projects != null }
    compose.onNodeWithContentDescription(createLabel("proj-a")).performScrollTo().performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.create is ScriptActState.Failed }

    compose.onNodeWithText("Create failed for proj-a (exit 4)").assertIsDisplayed()
    compose.onNodeWithText("pip: resolution impossible").assertIsDisplayed()
  }

  /** A run that never produces an exit is refused, and the port's words are what is shown. */
  @Test
  fun aCreateThePortRefusesIsSaidInItsWords() {
    val creation = FakeSpaceEnvironmentCreation()
    creation.eventsFor = { throw IllegalStateException("no proot on this device") }
    show(
      spaceEnvironmentCreation = creation,
      workspaceRepository = FakeWorkspaceRepository(mapOf("" to listOf(entry("proj-a")))),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.projects != null }
    compose.onNodeWithContentDescription(createLabel("proj-a")).performScrollTo().performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.create is ScriptActState.Refused }

    compose.onNodeWithText("no proot on this device").assertIsDisplayed()
  }

  /** One create at a time: while the script runs, the other projects' buttons wait. */
  @Test
  fun aSecondCreateWhileOneRunsIsNotOffered() {
    val creation = GatedSpaceEnvironmentCreation()
    show(
      spaceEnvironmentCreation = creation,
      workspaceRepository = FakeWorkspaceRepository(mapOf("" to listOf(entry("proj-a"), entry("proj-b")))),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.projects != null }
    compose.onNodeWithContentDescription(createLabel("proj-a")).performScrollTo().performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.create is ScriptActState.Running }

    compose.onNodeWithContentDescription(createLabel("proj-b")).assertIsNotEnabled()
    creation.gate.complete(Unit)
  }

  /** A project whose `.venv` is already an environment is offered no create - the script would refuse it. */
  @Test
  fun aProjectWithAVenvOffersNoCreate() {
    show(
      spaceEnvironments = FakeSpaceEnvironments(
        mapOf(BASE_SPACE.id to listOf(PythonEnvironment("proj-a/.venv", hasIpykernel = true))),
      ),
      workspaceRepository = FakeWorkspaceRepository(mapOf("" to listOf(entry("proj-a"), entry("proj-b")))),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.projects != null }

    compose.onNodeWithContentDescription(createLabel("proj-a")).assertIsNotEnabled()
    compose.onNodeWithContentDescription(createLabel("proj-b")).assertIsEnabled()
  }

  /** A `.venv` whose interpreter is gone is no `.venv` on disk, so its project can be created again. */
  @Test
  fun aGoneVenvDoesNotBlockCreate() {
    show(
      spaceEnvironments = FakeSpaceEnvironments(
        mapOf(
          BASE_SPACE.id to listOf(
            PythonEnvironment("proj-a/.venv", hasIpykernel = false, registeredAs = "proj-a-.venv", isMissing = true),
          ),
        ),
      ),
      workspaceRepository = FakeWorkspaceRepository(mapOf("" to listOf(entry("proj-a")))),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.projects != null }

    compose.onNodeWithContentDescription(createLabel("proj-a")).assertIsEnabled()
  }

  /** The Space's own interpreters are a trailing section, each row offering its Register. */
  @Test
  fun theSpacesInterpretersAreListedWithRegister() {
    show()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.interpreters != null }

    compose.onNodeWithText("Interpreters").assertExists()
    compose.onNodeWithText("/usr/bin/python3").performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription(registerLabel("/usr/bin/python3")).assertIsEnabled()
  }

  /** A registered interpreter's row says its kernel's name instead of offering Register again. */
  @Test
  fun aRegisteredInterpreterOffersNoRegister() {
    show(
      spaceKernelRegistration = FakeSpaceKernelRegistration(
        mapOf(
          BASE_SPACE.id to listOf(
            SpaceInterpreter("/usr/bin/python3", isGnu = false, registeredAs = "usr-bin-python3"),
          ),
        ),
      ),
    )
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.interpreters != null }

    compose.onNodeWithText(registeredLabel("usr-bin-python3")).assertExists()
    compose.onNodeWithContentDescription(registerLabel("/usr/bin/python3")).assertDoesNotExist()
  }

  /** A tap registers the interpreter through the port and the row shows the kernel's name. */
  @Test
  fun anInterpretersRegisterRunsTheScriptAndSaysTheKernel() {
    val registration = FakeSpaceKernelRegistration()
    show(spaceKernelRegistration = registration)
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.interpreters != null }

    compose.onNodeWithContentDescription(registerLabel("/usr/bin/python3")).performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { registration.registers.isNotEmpty() }
    compose.runOnIdle {
      assertEquals(listOf(KernelRegister(BASE_SPACE.id, "/usr/bin/python3")), registration.registers)
    }
    compose.waitUntil(TIMEOUT_MILLIS) {
      viewModel.uiState.value.interpreters?.single { it.path == "/usr/bin/python3" }?.registeredAs != null
    }
    compose.onNodeWithText(registeredLabel("usr-bin-python3")).assertExists()
  }

  /** A failed register keeps the exit and the script's tail on screen - its own refusal wording. */
  @Test
  fun aFailedInterpreterRegisterShowsTheExitAndTheOutputsTail() {
    val registration = FakeSpaceKernelRegistration()
    registration.eventsFor = {
      listOf(SpaceShellEvent.Output("no ipykernel in it"), SpaceShellEvent.Finished(2))
    }
    show(spaceKernelRegistration = registration)
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.interpreters != null }

    compose.onNodeWithContentDescription(registerLabel("/usr/bin/python3")).performScrollTo().performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.interpreterRegister is ScriptActState.Failed }

    compose.onNodeWithText("Register failed for /usr/bin/python3 (exit 2)").assertIsDisplayed()
    compose.onNodeWithText("no ipykernel in it").assertIsDisplayed()
  }

  private fun show(
    spaceEnvironments: SpaceEnvironments = FakeSpaceEnvironments(),
    spaceEnvironmentCreation: SpaceEnvironmentCreation = FakeSpaceEnvironmentCreation(),
    spaceKernelRegistration: SpaceKernelRegistration = FakeSpaceKernelRegistration(),
    workspaceRepository: WorkspaceRepository = FakeWorkspaceRepository(),
    spaceId: String = BASE_SPACE.id,
  ) {
    viewModel = EnvironmentsPanelViewModel(
      spaceId,
      spaceEnvironments,
      spaceEnvironmentCreation,
      spaceKernelRegistration,
      workspaceRepository,
    )
    compose.setContent {
      JupydroidTheme {
        EnvironmentsSidePanel(
          spaceEnvironments = spaceEnvironments,
          spaceEnvironmentCreation = spaceEnvironmentCreation,
          spaceKernelRegistration = spaceKernelRegistration,
          workspaceRepository = workspaceRepository,
          spaceId = spaceId,
          viewModel = viewModel,
        )
      }
    }
  }

  private fun entry(name: String) = WorkspaceEntry(name, name, WorkspaceEntryKind.DIRECTORY, Instant.EPOCH)

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}

/**
 * A creation whose run pauses on [gate] after its opening lines: the state a panel sits in while
 * pip downloads, held open so a test can read it. Records its asks the way
 * [FakeSpaceEnvironmentCreation] does.
 */
private class GatedSpaceEnvironmentCreation : SpaceEnvironmentCreation {
  val creates = mutableListOf<EnvironmentCreate>()
  val gate = CompletableDeferred<Unit>()

  override fun create(spaceId: String, project: String): Flow<SpaceShellEvent> = flow {
    creates += EnvironmentCreate(spaceId, project)
    emit(SpaceShellEvent.Output("Creating virtual environment"))
    gate.await()
    emit(SpaceShellEvent.Finished(0))
  }
}
