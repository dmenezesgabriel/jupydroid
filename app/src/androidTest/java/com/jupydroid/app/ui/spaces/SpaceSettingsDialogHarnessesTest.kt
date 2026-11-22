package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeHarnessScripts
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CHAT_HARNESS_CATALOG
import com.jupydroid.app.domain.model.ChatHarness
import com.jupydroid.app.domain.model.HarnessPresence
import com.jupydroid.app.domain.model.HarnessVersion
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.domain.port.SpaceShellEvent
import com.jupydroid.app.theme.JupydroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The harnesses section at the foot of a stopped Space's Settings: every catalog harness with
 * its live probe answer, an Install that streams the script's output, and - expanded - the
 * "what it adds" summary and the install script's own bytes, which are the bytes `SpaceShell`
 * is handed.
 *
 * The ViewModel's lifecycle is `SpaceHarnessesViewModelTest`'s; this holds what only a
 * composition can: that the states, the script and the install reach the screen.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSettingsDialogHarnessesTest {
  @get:Rule val compose = createComposeRule()

  private val installs = mutableListOf<ChatHarness>()

  private val opencode = CHAT_HARNESS_CATALOG.first { it.id == "opencode" }
  private val claude = CHAT_HARNESS_CATALOG.first { it.id == "claude-agent-acp" }

  @Test
  fun theSectionListsEveryCatalogHarness() {
    show(stateOf(opencode, claude))

    compose.onNodeWithTag(SPACE_HARNESSES_TAG).performScrollTo().assertExists()
    compose.onNodeWithText("OpenCode").assertExists()
    compose.onNodeWithText("Claude").assertExists()
  }

  @Test
  fun probeAnswersRenderTheirVerdicts() {
    show(
      stateOf(
        row(opencode, HarnessProbeState.Answered(HarnessPresence.NotInstalled)),
        row(claude, HarnessProbeState.Answered(HarnessPresence.Installed(HarnessVersion(0, 97, 0)))),
      ),
    )

    compose.onNodeWithText("Not installed").assertExists()
    compose.onNodeWithText("Installed · 0.97.0").assertExists()
  }

  @Test
  fun anInstalledHarnessWithNoVersionReadsInstalled() {
    show(stateOf(row(opencode, HarnessProbeState.Answered(HarnessPresence.Installed(null)))))

    compose.onNodeWithText("Installed").assertExists()
  }

  @Test
  fun aVersionTheChatRefusesSaysSo() {
    show(
      stateOf(
        row(
          opencode,
          HarnessProbeState.Answered(HarnessPresence.Incompatible(HarnessVersion(2, 5, 0))),
        ),
      ),
    )

    compose.onNodeWithText("2.5.0", substring = true).assertExists()
    compose.onNodeWithText("not a version the Chat accepts", substring = true).assertExists()
  }

  @Test
  fun aProbeStillRunningReadsChecking() {
    show(stateOf(row(opencode, HarnessProbeState.Probing)))

    compose.onNodeWithText("Checking…").assertExists()
  }

  @Test
  fun aProbeThatCouldNotAnswerShowsThePortsWords() {
    show(stateOf(row(opencode, HarnessProbeState.Failed("proot refused 'space-1'"))))

    compose.onNodeWithText("proot refused 'space-1'").assertExists()
  }

  @Test
  fun installIsOfferedPerHarnessAndHandsTheHarnessOver() {
    show(
      stateOf(
        row(opencode, HarnessProbeState.Answered(HarnessPresence.NotInstalled)),
        row(claude, HarnessProbeState.Answered(HarnessPresence.NotInstalled)),
      ),
    )

    compose.onNodeWithContentDescription(installLabel("OpenCode")).performScrollTo().performClick()
    compose.onNodeWithContentDescription(installLabel("Claude")).performScrollTo().performClick()

    assertEquals(listOf(opencode, claude), installs)
  }

  @Test
  fun aRunningInstallShowsItsOutputAndSaysToKeepTheAppOpen() {
    show(
      stateOf(
        row(
          opencode,
          HarnessProbeState.Answered(HarnessPresence.NotInstalled),
          install = HarnessInstallState.Running(listOf("fetching opencode 1.19.3", "fetch-seconds=8.1")),
        ),
      ),
    )

    compose.onNodeWithText("fetching opencode 1.19.3", substring = true).assertExists()
    compose.onNodeWithText("fetch-seconds=8.1", substring = true).assertExists()
    compose.onNodeWithText("Keep Jupydroid open", substring = true).assertExists()
    compose.onAllNodesWithContentDescription(installLabel("OpenCode")).assertCountEquals(0)
  }

  @Test
  fun aFailedInstallShowsTheExitAndTheOutputTail() {
    show(
      stateOf(
        row(
          opencode,
          HarnessProbeState.Answered(HarnessPresence.NotInstalled),
          install = HarnessInstallState.Failed(3, "npm ERR! code 1\nnpm ERR! Log files were not written"),
        ),
      ),
    )

    compose.onNodeWithText("exit 3", substring = true).assertExists()
    compose.onNodeWithText("npm ERR! Log files were not written", substring = true).assertExists()
  }

  @Test
  fun aRefusedInstallShowsTheMessage() {
    show(
      stateOf(
        row(
          opencode,
          HarnessProbeState.Answered(HarnessPresence.NotInstalled),
          install = HarnessInstallState.Refused("proot refused 'space-1'"),
        ),
      ),
    )

    compose.onNodeWithText("proot refused 'space-1'").assertExists()
  }

  @Test
  fun expandingShowsWhatItAddsAndTheScriptBytes() {
    val script = "#!/bin/sh\necho \"fake install of opencode\""
    show(
      stateOf(
        row(
          opencode,
          HarnessProbeState.Answered(HarnessPresence.NotInstalled),
          script = HarnessScriptState.Ready(script),
        ),
      ),
    )

    compose.onNodeWithText("fake install of opencode", substring = true).assertDoesNotExist()
    compose.onNodeWithText("OpenCode", substring = true).performScrollTo().performClick()

    compose.onNodeWithText(opencode.whatItAdds).assertExists()
    compose.onNodeWithText("fake install of opencode", substring = true).assertExists()
  }

  @Test
  fun aScriptStillLoadingSaysSoAndOneThatFailedSaysWhy() {
    show(
      stateOf(
        row(opencode, HarnessProbeState.Answered(HarnessPresence.NotInstalled)),
        row(
          claude,
          HarnessProbeState.Answered(HarnessPresence.NotInstalled),
          script = HarnessScriptState.Failed("no install script for 'claude-agent-acp'"),
        ),
      ),
    )

    compose.onNodeWithText("OpenCode", substring = true).performScrollTo().performClick()
    compose.onNodeWithText("Reading the install script", substring = true).assertExists()
    compose.onNodeWithText("Claude", substring = true).performScrollTo().performClick()
    compose.onNodeWithText("no install script for 'claude-agent-acp'").assertExists()
  }

  /**
   * The claim the whole feature rests on: the bytes expanded on screen are the bytes
   * `SpaceShell` is handed - through the real screen, the real ViewModel and the fakes.
   */
  @Test
  fun theExpandedScriptIsWhatSpaceShellIsHanded() {
    val shell = FakeSpaceShell()
    val scripts = FakeHarnessScripts()
    shell.eventsFor = { run ->
      if (run.script.contains("harness-probe:")) absentProbeEvents() else cleanInstallEvents()
    }
    showScreen(shell, scripts)
    compose.onNodeWithContentDescription(settingsThesis).performClick()
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithText("Not installed").fetchSemanticsNodes().isNotEmpty()
    }

    compose.onNodeWithText("OpenCode", substring = true).performScrollTo().performClick()
    val shownScript = runBlocking { scripts.installScript(opencode) }
    compose.onNodeWithText("fake install of opencode", substring = true).assertExists()

    compose.onNodeWithContentDescription(installLabel("OpenCode")).performScrollTo().performClick()
    compose.waitUntil(TIMEOUT_MILLIS) { shell.runs.any { it.script == shownScript } }

    val installRun = shell.runs.single { it.script == shownScript }
    assertEquals(thesis.id, installRun.spaceId)
    assertEquals(HARNESS_INSTALL_TIMEOUT, installRun.timeout)
    assertTrue(shownScript.isNotBlank())
  }

  private fun absentProbeEvents() = listOf(
    SpaceShellEvent.Output("harness-probe:absent"),
    SpaceShellEvent.Finished(0),
  )

  private fun cleanInstallEvents() = listOf(
    SpaceShellEvent.Output("fake install done"),
    SpaceShellEvent.Finished(0),
  )

  private fun stateOf(vararg harnesses: ChatHarness) = SpaceHarnessesState(
    harnesses.map {
      HarnessRowState(it)
    }.toList(),
  )

  private fun stateOf(vararg rows: HarnessRowState) = SpaceHarnessesState(rows.toList())

  private fun row(
    harness: ChatHarness,
    probe: HarnessProbeState,
    install: HarnessInstallState = HarnessInstallState.Idle,
    script: HarnessScriptState = HarnessScriptState.Loading,
  ) = HarnessRowState(harness = harness, probe = probe, install = install, script = script)

  private fun show(state: SpaceHarnessesState) {
    compose.setContent {
      JupydroidTheme {
        SpaceSettingsDialog(
          space = thesis,
          harnesses = state,
          onHarnessInstall = { installs += it },
          onSave = {},
          onDismiss = {},
        )
      }
    }
  }

  private fun showScreen(shell: FakeSpaceShell, scripts: FakeHarnessScripts) {
    val repository = FakeSpaceRepository(listOf(base, thesis))
    val servers = FakeSpaceServers()
    val environments = FakeSpaceEnvironments(mapOf(BASE_SPACE.id to emptyList(), thesis.id to emptyList()))
    val kernelSpecs = FakeKernelSpecs()
    val spacesModel = SpacesViewModel(repository, servers, environments, kernelSpecs)
    val harnessesModel = SpaceHarnessesViewModel(shell, scripts)
    compose.setContent {
      JupydroidTheme {
        SpacesHomeScreen(
          repository,
          servers,
          environments,
          kernelSpecs,
          spaceShell = shell,
          harnessScripts = scripts,
          onOpen = {},
          viewModel = spacesModel,
          harnessesViewModel = harnessesModel,
        )
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { spacesModel.uiState.value.spaces.isNotEmpty() }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 10_000L
    val base = defaultSpaceConfig(BASE_SPACE.id, Instant.EPOCH)
    val thesis = SpaceConfig(id = "space-1", name = "Thesis", created = Instant.EPOCH)
    val settingsThesis = "Settings Thesis (space-1)"
  }
}
