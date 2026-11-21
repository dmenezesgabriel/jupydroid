@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.spaces

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeHarnessScripts
import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.data.fake.SpaceShellRun
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CHAT_HARNESS_CATALOG
import com.jupydroid.app.domain.model.HarnessPresence
import com.jupydroid.app.domain.model.HarnessVersion
import com.jupydroid.app.domain.model.probeScript
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration

/**
 * What the harnesses section's ViewModel asks of [SpaceShell] and [HarnessScripts]: a probe of
 * every catalog harness when the Space's Settings opens, and one bounded install per harness whose
 * streamed output, re-probe and failure the rows report.
 */
class SpaceHarnessesViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val shell = FakeSpaceShell()
  private val scripts = FakeHarnessScripts()

  private fun harness(id: String) = CHAT_HARNESS_CATALOG.first { it.id == id }

  private fun SpaceHarnessesViewModel.row(id: String) = uiState.value.harnesses.first { it.harness.id == id }

  /** The catalog's rows are there before any probe answers, each still probing and installable-idle. */
  @Test
  fun theCatalogRowsAreThereBeforeAnyProbeAnswers() {
    val model = SpaceHarnessesViewModel(shell, scripts)

    assertEquals(CHAT_HARNESS_CATALOG, model.uiState.value.harnesses.map { it.harness })
    model.uiState.value.harnesses.forEach { row ->
      assertTrue(row.probe is HarnessProbeState.Probing)
      assertEquals(HarnessInstallState.Idle, row.install)
    }
  }

  /** Opening probes every catalog harness in the given Space, each bounded by the probe's own timeout. */
  @Test
  fun openProbesEveryCatalogHarnessInTheSpace() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { absentProbeEvents() }

    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(CHAT_HARNESS_CATALOG.size, shell.runs.size)
    CHAT_HARNESS_CATALOG.forEach { catalogHarness ->
      val run = shell.runs.single { it.script == catalogHarness.probeScript() }
      assertEquals(BASE_SPACE.id, run.spaceId)
      assertEquals(HARNESS_PROBE_TIMEOUT, run.timeout)
      assertTrue(model.row(catalogHarness.id).probe is HarnessProbeState.Answered)
    }
  }

  /** A second open probes the Space it is given, not the first one's. */
  @Test
  fun openProbesTheSpaceItIsGiven() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { absentProbeEvents() }

    model.open("space-1")
    advanceUntilIdle()
    model.open("space-2")
    advanceUntilIdle()

    val secondBatch = shell.runs.drop(CHAT_HARNESS_CATALOG.size)
    assertEquals(CHAT_HARNESS_CATALOG.size, secondBatch.size)
    assertTrue(secondBatch.all { it.spaceId == "space-2" })
  }

  @Test
  fun aProbeThatFindsNothingReadsNotInstalled() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { absentProbeEvents() }

    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(HarnessProbeState.Answered(HarnessPresence.NotInstalled), model.row("opencode").probe)
  }

  /** opencode's persona reads `--version` too, so an in-range semver is part of "installed". */
  @Test
  fun aProbeThatFindsAnInRangeVersionReadsInstalled() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { run ->
      if (run.script == harness("opencode").probeScript()) installedProbeEvents("1.19.3") else absentProbeEvents()
    }

    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    assertEquals(installedAnswer(), model.row("opencode").probe)
  }

  /** A run that ends without the probe's markers did not answer - the row says so, not "checking". */
  @Test
  fun aProbeThatDoesNotAnswerFailsTheRow() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { listOf(SpaceShellEvent.Finished(0)) }

    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    assertTrue(model.row("opencode").probe is HarnessProbeState.Failed)
  }

  /** A refused run is the port's own words on the row, as every other surface shows refusals. */
  @Test
  fun aProbeTheShellRefusesFailsTheRowInThePortsWords() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { throw IllegalArgumentException("no Space has id 'space-404'") }

    model.open("space-404")
    advanceUntilIdle()

    val failed = model.row("opencode").probe as HarnessProbeState.Failed
    assertTrue(failed.message.contains("space-404"))
  }

  /** The install runs the port's script bytes - the same string the section shows - bounded. */
  @Test
  fun anInstallRunsThePortsScriptBounded() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { run -> if (run.isProbe()) absentProbeEvents() else cleanInstallEvents() }
    model.open(BASE_SPACE.id)
    advanceUntilIdle()
    val installScript = scripts.installScript(harness("opencode"))

    model.install(harness("opencode"))
    advanceUntilIdle()

    val run = shell.runs.single { it.script == installScript }
    assertEquals(BASE_SPACE.id, run.spaceId)
    assertEquals(HARNESS_INSTALL_TIMEOUT, run.timeout)
  }

  /**
   * While the script runs, the row carries its output lines as they arrive - a person watches the
   * install work rather than a spinner alone.
   */
  @Test
  fun anInstallStreamsItsOutputLinesIntoTheRow() = runTest {
    val gate = CompletableDeferred<Unit>()
    val gated = GatedSpaceShell()
    val model = SpaceHarnessesViewModel(gated, scripts)
    gated.eventsFor = streamingOrAbsent(gate)
    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    model.install(harness("opencode"))
    advanceUntilIdle()

    val running = model.row("opencode").install as HarnessInstallState.Running
    assertEquals(listOf("fetching opencode 1.19.3", "fetch-seconds=8.1"), running.lines)

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(HarnessInstallState.Idle, model.row("opencode").install)
  }

  /**
   * A clean exit re-probes the harness - "installed" is a live answer, never the install's own
   * say-so - and the row goes back to idle with the new presence.
   */
  @Test
  fun aCleanInstallReprobesTheHarness() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = reprobeAnswersInstalled()
    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    model.install(harness("opencode"))
    advanceUntilIdle()

    assertEquals(HarnessInstallState.Idle, model.row("opencode").install)
    assertEquals(installedAnswer(), model.row("opencode").probe)
  }

  /**
   * A probe still in flight when the install starts is cancelled: its answer would describe the
   * Space before the install, and landing after the re-probe it would read installed as absent.
   */
  @Test
  fun anInstallCancelsTheHarnessesPendingProbe() = runTest {
    val staleProbeRelease = CompletableDeferred<Unit>()
    val gated = GatedSpaceShell()
    val model = SpaceHarnessesViewModel(gated, scripts)
    gated.eventsFor = staleFirstProbe(staleProbeRelease)
    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    model.install(harness("opencode"))
    advanceUntilIdle()
    staleProbeRelease.complete(Unit)
    advanceUntilIdle()

    assertEquals(installedAnswer(), model.row("opencode").probe)
  }

  /** A non-zero exit keeps the output's tail - what a person needs to see why it failed. */
  @Test
  fun aFailedInstallKeepsTheTailOfItsOutput() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { run -> if (run.isProbe()) absentProbeEvents() else failedInstallEvents() }
    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    model.install(harness("opencode"))
    advanceUntilIdle()

    val failed = model.row("opencode").install as HarnessInstallState.Failed
    assertEquals(3, failed.exitCode)
    assertTrue(failed.excerpt.contains("npm ERR! Log files were not written"))
  }

  /** A run that never produced an exit is the port's refusal, not an exit code. */
  @Test
  fun anInstallTheShellRefusesIsReportedInThePortsWords() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = { run ->
      if (run.isProbe()) absentProbeEvents() else throw IllegalStateException("proot refused 'base'")
    }
    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    model.install(harness("opencode"))
    advanceUntilIdle()

    val refused = model.row("opencode").install as HarnessInstallState.Refused
    assertTrue(refused.message.contains("proot refused 'base'"))
  }

  /** Dismissing the dialog cancels the install's collection - the scripts resume on a re-run. */
  @Test
  fun dismissCancelsARunningInstall() = runTest {
    var installCollectCancelled = false
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = hangingInstallEvents { installCollectCancelled = true }
    model.open(BASE_SPACE.id)
    advanceUntilIdle()

    model.install(harness("opencode"))
    advanceUntilIdle()
    assertTrue(model.row("opencode").install is HarnessInstallState.Running)

    model.dismiss()
    advanceUntilIdle()

    assertTrue(installCollectCancelled)
    assertEquals(HarnessInstallState.Idle, model.row("opencode").install)
  }

  /** A second tap while an install runs does not start a second one. */
  @Test
  fun aSecondInstallOfTheSameHarnessIsRefusedWhileOneRuns() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)
    shell.eventsFor = hangingInstallEvents()
    model.open(BASE_SPACE.id)
    advanceUntilIdle()
    val installScript = scripts.installScript(harness("opencode"))

    model.install(harness("opencode"))
    model.install(harness("opencode"))
    advanceUntilIdle()

    assertEquals(1, shell.runs.count { it.script == installScript })
  }

  /** Before the dialog opens there is no Space to install into - the tap does nothing. */
  @Test
  fun anInstallBeforeOpenDoesNothing() = runTest {
    val model = SpaceHarnessesViewModel(shell, scripts)

    model.install(harness("opencode"))
    advanceUntilIdle()

    assertTrue(shell.runs.isEmpty())
  }
}

/** A probe run is the only one whose script carries the probe's own marker prefix. */
private fun SpaceShellRun.isProbe() = script.contains("harness-probe:")

private val opencodeProbeScript = CHAT_HARNESS_CATALOG.first { it.id == "opencode" }.probeScript()

private fun installedAnswer() = HarnessProbeState.Answered(HarnessPresence.Installed(HarnessVersion(1, 19, 3)))

private fun absentProbeEvents() = listOf(
  SpaceShellEvent.Output("harness-probe:absent"),
  SpaceShellEvent.Finished(0),
)

private fun installedProbeEvents(version: String) = listOf(
  SpaceShellEvent.Output("harness-probe:path=/usr/local/bin/opencode"),
  SpaceShellEvent.Output("opencode $version"),
  SpaceShellEvent.Output("harness-probe:version-rc=0"),
  SpaceShellEvent.Finished(0),
)

private fun cleanInstallEvents() = listOf(
  SpaceShellEvent.Output("installing"),
  SpaceShellEvent.Finished(0),
)

private fun failedInstallEvents() = listOf(
  SpaceShellEvent.Output("npm ERR! code 1"),
  SpaceShellEvent.Output("npm ERR! Log files were not written"),
  SpaceShellEvent.Finished(3),
)

/** The first opencode probe answers absent, every later one installed - the shape a re-probe sees. */
private fun reprobeAnswersInstalled(): suspend (SpaceShellRun) -> List<SpaceShellEvent> {
  var opencodeProbes = 0
  return { run ->
    when {
      !run.isProbe() -> cleanInstallEvents()
      run.script == opencodeProbeScript && ++opencodeProbes > 1 -> installedProbeEvents("1.19.3")
      else -> absentProbeEvents()
    }
  }
}

/**
 * Probe answers absent; an install emits two output lines then holds on [gate] before its exit -
 * long enough for a test to read the row mid-run.
 */
private fun streamingOrAbsent(gate: CompletableDeferred<Unit>): suspend (SpaceShellRun) -> Flow<SpaceShellEvent> =
  { run ->
    if (run.isProbe()) {
      flow { absentProbeEvents().forEach { emit(it) } }
    } else {
      flow {
        emit(SpaceShellEvent.Output("fetching opencode 1.19.3"))
        emit(SpaceShellEvent.Output("fetch-seconds=8.1"))
        gate.await()
        emit(SpaceShellEvent.Finished(0))
      }
    }
  }

/**
 * The stale-probe race's answers: the first opencode probe hangs on [gate] so a test can release
 * its answer after the install's re-probe landed; every later opencode probe answers installed.
 */
private fun staleFirstProbe(gate: CompletableDeferred<Unit>): suspend (SpaceShellRun) -> Flow<SpaceShellEvent> {
  var opencodeProbes = 0
  return { run ->
    when {
      run.script == opencodeProbeScript && ++opencodeProbes == 1 -> gatedAbsentProbe(gate)
      run.script == opencodeProbeScript -> flow { installedProbeEvents("1.19.3").forEach { emit(it) } }
      run.isProbe() -> flow { absentProbeEvents().forEach { emit(it) } }
      else -> flow {
        emit(SpaceShellEvent.Output("done"))
        emit(SpaceShellEvent.Finished(0))
      }
    }
  }
}

private fun gatedAbsentProbe(gate: CompletableDeferred<Unit>) = flow {
  gate.await()
  absentProbeEvents().forEach { emit(it) }
}

/**
 * Probes answer absent; installs never finish on their own - [onCancel] fires when the port's
 * collection is cancelled, which is how a test knows a dismiss reached the run.
 */
private fun hangingInstallEvents(onCancel: () -> Unit = {}): suspend (SpaceShellRun) -> List<SpaceShellEvent> = { run ->
  if (run.isProbe()) {
    absentProbeEvents()
  } else {
    try {
      awaitCancellation()
    } finally {
      onCancel()
    }
  }
}

/**
 * A [SpaceShell] whose runs emit whatever [eventsFor] flows - able to hold mid-stream on a gate,
 * where [FakeSpaceShell] answers a whole list at once. Runs are recorded the same way.
 */
private class GatedSpaceShell : SpaceShell {
  val runs = mutableListOf<SpaceShellRun>()

  var eventsFor: suspend (SpaceShellRun) -> Flow<SpaceShellEvent> = {
    flow { emit(SpaceShellEvent.Finished(0)) }
  }

  override fun run(spaceId: String, script: String, timeout: Duration): Flow<SpaceShellEvent> = flow {
    val run = SpaceShellRun(spaceId, script, timeout)
    runs += run
    eventsFor(run).collect { emit(it) }
  }
}
