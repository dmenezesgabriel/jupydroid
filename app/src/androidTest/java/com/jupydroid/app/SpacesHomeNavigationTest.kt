package com.jupydroid.app

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.data.fake.FakeSharedDocumentReader
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.import.SharedNotebook
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The Spaces home as the app's start destination: a cold start lands on it, base's Open enters the
 * workspace, and Back from the workspace's resting state comes back to it.
 *
 * Whether the stack holds the right keys is `SpacesHomeBackStackTest`'s. This is what only a
 * composition answers: which screen the scene strategy actually draws for that stack.
 */
@RunWith(AndroidJUnit4::class)
class SpacesHomeNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  private val spaceServers = FakeSpaceServers()

  @Test
  fun aColdStartLandsOnTheHome() {
    startApp(twoPane = false)

    waitForText(BASE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertIsDisplayed()
    compose.onNodeWithText(LISTED_FILE).assertDoesNotExist()
  }

  @Test
  fun openingBaseShowsItsWorkspace() {
    startApp(twoPane = false)

    compose.enterBaseSpace()

    waitForText(LISTED_FILE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertDoesNotExist()
  }

  /** At one pane the browser is the workspace's resting state, so Back from it is Back to the home. */
  @Test
  fun backFromTheBrowserReturnsToTheHomeAtOnePane() {
    startApp(twoPane = false)
    compose.enterBaseSpace()
    waitForText(LISTED_FILE)

    Espresso.pressBack()

    waitForText(BASE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertIsDisplayed()
  }

  /**
   * `maybeCreate` fills an empty *main area*, and the home has none: the home is not a workspace,
   * so no launcher is drawn over it however wide the window is.
   */
  @Test
  fun noLauncherCoversTheHomeAtTwoPanes() {
    startApp(twoPane = true)

    waitForText(BASE)
    compose.waitForIdle()
    compose.onNodeWithTag(LAUNCHER_SCREEN).assertDoesNotExist()
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertIsDisplayed()
  }

  /**
   * The activity bar is the workspace's, so the home is drawn without it: a rail beside the home
   * would offer Files and a new launcher before any Space had been entered.
   */
  @Test
  fun theHomeHasNoActivityBarAtTwoPanes() {
    startApp(twoPane = true)

    waitForText(BASE)
    compose.onAllNodesWithContentDescription(FILES).assertCountEquals(0)
  }

  @Test
  fun theWorkspaceHasItsActivityBarAtTwoPanes() {
    startApp(twoPane = true)

    compose.enterBaseSpace()

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
  }

  /**
   * An imported notebook opens inside base's workspace, with the browser beside it - not on the
   * bare home, where there is no list pane for it to pair with. Base is run on the way in, since a
   * Space is only entered running, and what lands there is the shared file as it was served.
   */
  @Test
  fun anImportOpensInsideTheWorkspace() {
    startApp(twoPane = true, sharedNotebook = SharedNotebook(SHARED_URI, SHARED_NOTEBOOK))

    waitForText(LISTED_FILE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertDoesNotExist()
    assertEquals(setOf(BASE_SPACE.id), spaceServers.running.value)
    assertEquals(SHARED_JSON, File(workspaceOf(BASE_SPACE.id), SHARED_NOTEBOOK).readText())
  }

  /**
   * Thesis's workspace is its own: entering it lists Thesis's files and none of base's, which is
   * what binding the files to the browser's key buys.
   */
  @Test
  fun openingAnotherSpaceShowsItsOwnWorkspaceAndNotBases() {
    startApp(twoPane = false)

    compose.enterSpace(THESIS_NAME, THESIS_ID)

    waitForText(THESIS_FILE)
    compose.onNodeWithText(LISTED_FILE).assertDoesNotExist()
  }

  /**
   * A cell run inside Thesis opens its session on Thesis's kernel ports, and base's Running list
   * stays empty - what binding the kernel side to the browser's key buys.
   */
  @Test
  fun aCellRunInAnotherSpaceOpensItsSessionThereAndNotInBase() {
    runBlocking {
      DiskNotebookRepository(workspaceOf(THESIS_ID))
        .saveNotebook(Notebook(THESIS_NOTEBOOK, THESIS_NOTEBOOK, "python3", listOf(Cell("c1", CellKind.CODE, "1"))))
    }
    startApp(twoPane = false)
    compose.enterSpace(THESIS_NAME, THESIS_ID)
    waitForText(THESIS_NOTEBOOK)

    compose.onNode(hasText(THESIS_NOTEBOOK) and hasContentDescription("File")).performClick()
    compose.onAllNodesWithContentDescription("Run cell")[0].performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { runningIn(THESIS_ID).isNotEmpty() }

    assertEquals(listOf(THESIS_NOTEBOOK), runningIn(THESIS_ID))
    assertEquals(emptyList<String>(), runningIn(BASE_SPACE.id))
  }

  /**
   * A shell opened inside Thesis runs on Thesis's terminal port, and base's list stays empty - so
   * Thesis's Running panel shows it and base's never does.
   */
  @Test
  fun aTerminalOpenedInAnotherSpaceIsListedThereAndNotInBase() {
    startApp(twoPane = false)
    compose.enterSpace(THESIS_NAME, THESIS_ID)
    waitForText(THESIS_FILE)

    compose.onNodeWithContentDescription("More").performClick()
    compose.onNodeWithText("New terminal").performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { terminalsIn(THESIS_ID).isNotEmpty() }

    assertEquals(listOf("1"), terminalsIn(THESIS_ID))
    assertEquals(emptyList<String>(), terminalsIn(BASE_SPACE.id))
  }

  private fun terminalsIn(spaceId: String): List<String> =
    runBlocking { container.portsOf(spaceId).terminalGateway.running().map { it.name } }

  private fun runningIn(spaceId: String): List<String> =
    runBlocking { container.portsOf(spaceId).kernelSessions.running().map { it.path } }

  private fun startApp(twoPane: Boolean, sharedNotebook: SharedNotebook? = null) {
    File(workspaceOf(BASE_SPACE.id), LISTED_FILE).writeText("a file for the listing")
    File(workspaceOf(THESIS_ID), THESIS_FILE).writeText("a file in Thesis")
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowFor(twoPane))) {
        JupyterDroidTheme {
          MainNavigation(
            appContainer = container,
            sharedNotebook = sharedNotebook,
            onImportConsumed = {},
            twoPane = twoPane,
          )
        }
      }
    }
  }

  private val container by lazy {
    AppContainer(
      enteredSpacePorts = { spaceId, _ ->
        EnteredSpacePorts(
          notebookRepository = DiskNotebookRepository(workspaceOf(spaceId)),
          workspaceRepository = DiskWorkspaceRepository(workspaceOf(spaceId)),
          workspaceFiles = DiskWorkspaceFiles(workspaceOf(spaceId)),
          space = spaceWithId(spaceId),
        )
      },
      spaceRepository = FakeSpaceRepository(
        listOf(
          defaultSpaceConfig(BASE_SPACE.id, Instant.EPOCH),
          SpaceConfig(id = THESIS_ID, name = THESIS_NAME, created = Instant.EPOCH),
        ),
      ),
      launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
      spaceServers = spaceServers,
      sharedDocumentReader = FakeSharedDocumentReader(mapOf(SHARED_URI to SHARED_JSON.toByteArray())),
    )
  }

  /** One directory per Space, as `spaces/<id>/home/jupydroid` is on a device. */
  private fun workspaceOf(spaceId: String): File = File(tempFolder.root, spaceId).apply { mkdirs() }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val BASE = "Base"
    const val FILES = "Files"
    const val LISTED_FILE = "report.txt"
    const val LAUNCHER_SCREEN = "launcher-screen"
    const val TIMEOUT_MILLIS = 5_000L
    const val THESIS_ID = "space-1"
    const val THESIS_NAME = "Thesis"
    const val THESIS_FILE = "chapter.txt"
    const val THESIS_NOTEBOOK = "chapter.ipynb"
    const val SHARED_URI = "content://media/external/file/7"
    const val SHARED_NOTEBOOK = "Imported.ipynb"
    const val SHARED_JSON = """{"cells": [], "metadata": {}, "nbformat": 4, "nbformat_minor": 5}"""
  }
}
