package com.jupydroid.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * What the tab strip draws **after the activity is relaunched**, which a rotation does on every
 * turn of the device: the order the tabs were opened in, the name a tab was renamed to, and the
 * title a terminal's shell reported.
 *
 * Instrumented for [OpenDocumentsRecreationTest]'s reason: what survives a recreation is the
 * framework's to decide. Each test asserts its fact once before the relaunch, so a red after it
 * is the relaunch's and not the setup's.
 *
 * Two panes for [OpenDocumentMarkingTest]'s reason: the explorer's row marking is a list-pane
 * fact, and the row is also how a second document is opened beside the first.
 */
@RunWith(AndroidJUnit4::class)
class TabStripRecreationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @get:Rule val temp = TemporaryFolder()

  private val terminalGateway = FakeTerminalGateway()

  /** One per process, as `JupydroidApplication` builds it - a relaunch gets the same one. */
  private val appContainer by lazy {
    AppContainer(
      launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
      enteredSpacePorts = { _, _ ->
        EnteredSpacePorts(
          notebookRepository = DiskNotebookRepository(temp.root),
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          workspaceFiles = DiskWorkspaceFiles(temp.root),
          terminalGateway = terminalGateway,
        )
      },
    )
  }

  /** `activateById` never moves a tab, and a relaunch must not either - the stack is in activation order. */
  @Test
  fun theStripKeepsItsOpeningOrderAfterTheActivityIsRelaunched() {
    enterAWorkspaceHolding(FIRST, SECOND)
    open(FIRST)
    open(SECOND)
    compose.onNodeWithContentDescription("Tab: $FIRST").performClick()
    assertEquals(listOf(FIRST, SECOND), documentTabsLeftToRight())

    relaunch()
    compose.waitForTab(FIRST)
    compose.waitForTab(SECOND)

    assertEquals(listOf(FIRST, SECOND), documentTabsLeftToRight())
  }

  /**
   * The key keeps the name it was opened under; the name it moved to is held beside it. Beneath
   * the top first, where no screen is composed to report it again, then on top, where the row
   * marking reads it.
   */
  @Test
  fun aRenamedTabKeepsItsNameAndItsMarkAfterTheActivityIsRelaunched() {
    enterAWorkspaceHolding(FIRST, SECOND)
    open(FIRST)
    compose.renameTab(from = FIRST, to = RENAMED)
    compose.waitForTab(RENAMED)
    open(SECOND)

    relaunch()
    compose.waitForTab(SECOND)
    compose.onNodeWithContentDescription("Tab: $RENAMED").assertExists().performClick()

    compose.waitForRow(RENAMED).assertIsSelected()
  }

  /**
   * A terminal beneath the top tab does not compose, so its screen cannot report the title again:
   * what the strip draws after the relaunch is what it held through it.
   */
  @Test
  fun aTerminalBeneathTheTopKeepsItsReportedTitleAfterTheActivityIsRelaunched() {
    enterAWorkspaceHolding(FIRST)
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_TERMINAL.name}").performClick()
    compose.waitForTab("Terminal 1")
    // OSC 0, "set icon name and window title": the fake echoes it, and the emulator reads it as the
    // shell's own `printf '\e]0;…\a'` would be read.
    runBlocking { terminalGateway.send(TerminalSession("1"), "\u001b]0;$SHELL_TITLE\u0007") }
    compose.waitForTab(SHELL_TITLE)
    open(FIRST)

    relaunch()
    compose.waitForTab(FIRST)

    compose.onNodeWithContentDescription("Tab: $SHELL_TITLE").assertExists()
  }

  private fun enterAWorkspaceHolding(vararg names: String) {
    names.forEach { File(temp.root, it).writeText("seed") }
    showTheShell()
    compose.enterBaseSpace()
  }

  private fun open(name: String) {
    compose.waitForRow(name).performClick()
    compose.waitForTab(name)
  }

  private fun relaunch() {
    compose.activityRule.scenario.recreate()
    showTheShell()
  }

  /** The `.py` tabs only: whether the launcher fill keeps a tab beside them is not this test's question. */
  private fun documentTabsLeftToRight(): List<String> =
    compose.onAllNodes(hasContentDescription("Tab: ", substring = true))
      .fetchSemanticsNodes()
      .sortedBy { it.boundsInRoot.left }
      .map { it.config[SemanticsProperties.ContentDescription].joinToString().removePrefix("Tab: ") }
      .filter { it.endsWith(".py") }

  /** Through the activity rather than the rule: the relaunched activity is a new one to fill. */
  private fun showTheShell() {
    compose.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowFor(twoPane = true))) {
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
  }

  private companion object {
    const val FIRST = "first.py"
    const val SECOND = "second.py"
    const val RENAMED = "renamed.py"
    const val SHELL_TITLE = "reports"
  }
}
