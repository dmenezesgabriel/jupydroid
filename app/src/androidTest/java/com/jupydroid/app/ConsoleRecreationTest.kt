package com.jupydroid.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.console.CONSOLE_INPUT_FIELD_TAG
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import com.jupydroid.app.ui.shell.NEW_LAUNCHER
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * What an open Console draws **after the activity is relaunched** - a rotation relaunches it, and
 * so does a dark-mode switch. The `Console` key is saved with the stack and its `ConsoleViewModel`
 * is kept in the entry's store, but the session the entry composes against is handed in from
 * beside the stack, and the numbering of the next Console is held there too.
 *
 * Instrumented for [OpenDocumentsRecreationTest]'s reason: what survives a recreation is the
 * framework's to decide. `FakeKernelGateway`, `AppContainer`'s default: the question is whether a
 * pane is composed, not what a kernel answers.
 */
@RunWith(AndroidJUnit4::class)
class ConsoleRecreationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @get:Rule val temp = TemporaryFolder()

  /** One per process, as `JupydroidApplication` builds it - a relaunch gets the same one. */
  private val appContainer by lazy {
    AppContainer(
      launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
      enteredSpacePorts = { _, _ ->
        EnteredSpacePorts(
          notebookRepository = DiskNotebookRepository(temp.root),
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          workspaceFiles = DiskWorkspaceFiles(temp.root),
        )
      },
    )
  }

  /** The tab alone is not the Console: the pane under it must draw its prompt again. */
  @Test
  fun anOpenConsoleStillDrawsItsPromptAfterTheActivityIsRelaunched() {
    enterTheShell()
    openAConsoleCard()
    compose.waitForTab("Console 1")
    waitForPrompt()

    relaunch()
    compose.waitForTab("Console 1")

    waitForPrompt()
  }

  /** `Private.count` is the shell's, not the activity's: a relaunch does not restart it. */
  @Test
  fun theNextConsoleIsNumberedOnAfterTheActivityIsRelaunched() {
    enterTheShell()
    openAConsoleCard()
    compose.waitForTab("Console 1")

    relaunch()
    compose.waitForTab("Console 1")
    openAConsoleCard()

    compose.waitForTab("Console 2")
  }

  private fun enterTheShell() {
    showTheShell()
    compose.enterBaseSpace()
  }

  /** Through the rail's `+`, which two panes keep reachable while a Console is open. */
  private fun openAConsoleCard() {
    compose.onNodeWithContentDescription(NEW_LAUNCHER).performClick()
    waitForTag("launcher-screen")
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_CONSOLE.name}").performClick()
  }

  private fun waitForPrompt() {
    val drawn = runCatching { waitForTag(CONSOLE_INPUT_FIELD_TAG) }.isSuccess
    assertTrue("the Console's input field ($CONSOLE_INPUT_FIELD_TAG) was not drawn", drawn)
  }

  private fun waitForTag(tag: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  }

  private fun relaunch() {
    compose.activityRule.scenario.recreate()
    showTheShell()
  }

  /** Through the activity rather than the rule: the relaunched activity is a new one to fill. */
  private fun showTheShell() {
    compose.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        JupyterDroidTheme {
          MainNavigation(
            appContainer = appContainer,
            sharedNotebook = null,
            onImportConsumed = {},
            twoPane = true,
          )
        }
      }
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
