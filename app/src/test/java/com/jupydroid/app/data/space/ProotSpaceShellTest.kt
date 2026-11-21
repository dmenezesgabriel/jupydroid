package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * What only this implementation can be asked, beside
 * [com.jupydroid.app.domain.port.SpaceShellContract]: the order it prepares a Space in, the status
 * and the streams it hands back, and that a cancelled run stops its script. Real processes under
 * [FakeProotScript], so the exit is the OS's, not a flag's.
 */
class ProotSpaceShellTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val spaceDir by lazy { tempFolder.newFolder("space") }
  private val nativeDir by lazy { FakeProotScript.installIn(tempFolder.newFolder("native")) }

  private fun shell(
    installSpace: suspend (String) -> Unit = {},
    settingsFor: suspend (Space) -> SpaceSettings = { SpaceSettings() },
  ): ProotSpaceShell {
    FakeSpaceShellScript.installIn(spaceDir)
    return ProotSpaceShell(
      installSpace = installSpace,
      settingsFor = settingsFor,
      runtimeFor = { _, _ -> SpaceRuntime(spaceDir, nativeDir) },
      dirsFor = { SpaceDirs(spaceDir) },
    )
  }

  /**
   * The settings answer before the image is installed: an id nothing lists is refused while the
   * Space's directory is still only a name, before `installOnStartOf` could extract an image into
   * a directory no record names.
   */
  @Test
  fun theSettingsAreReadBeforeTheSpaceIsInstalled() = runTest {
    val order = mutableListOf<String>()
    val shell = shell(
      installSpace = { order += "install" },
      settingsFor = {
        order += "settings"
        SpaceSettings()
      },
    )

    shell.run("space-1", "true", 1.minutes).toList()

    assertEquals(listOf("settings", "install"), order)
  }

  /** The script's own status, not proot's - a `Finished` is what the section branches on. */
  @Test
  fun theExitStatusComesBack() = runTest {
    val events = shell().run("base", "exit 3", 1.minutes).toList()

    assertEquals(SpaceShellEvent.Finished(3), events.last())
  }

  /** stderr is merged into the same stream, as `startupScriptProcess` merges it into the log. */
  @Test
  fun stderrIsStreamedWithStdout() = runTest {
    val events = shell().run("base", "echo out; echo err >&2", 1.minutes).toList()
    val lines = events.filterIsInstance<SpaceShellEvent.Output>().map { it.line }

    assertTrue("stdout missing: $lines", "out" in lines)
    assertTrue("stderr missing: $lines", "err" in lines)
    assertEquals(SpaceShellEvent.Finished(0), events.last())
  }

  /**
   * A script that outruns its collector still ends `Finished`: more lines than the channel's
   * buffer holds must all arrive, in order, with the status last - a `trySend` for the terminal
   * event would drop it into a full buffer and end the run without one.
   */
  @Test
  fun aScriptOutrunningItsCollectorStillEndsFinished() = runBlocking {
    val flood = "i=0; while [ \$i -lt 200 ]; do echo line\$i; i=\$((i + 1)); done"

    // A collector this far behind keeps the channel's buffer full when the script ends, which is
    // when the terminal event is sent - real time, so `runBlocking`, not `runTest`'s clock.
    val events = shell().run("base", flood, 1.minutes).onEach { delay(2) }.toList()

    val lines = events.filterIsInstance<SpaceShellEvent.Output>().map { it.line }
    assertEquals(200, lines.size)
    assertEquals("line0", lines.first())
    assertEquals("line199", lines.last())
    assertEquals(SpaceShellEvent.Finished(0), events.last())
  }

  /**
   * A cancelled run destroys the script's process: the script sleeps, then would leave `late` in
   * the Space - and it never does, because the cancel killed it first. The window is real time,
   * so this is `runBlocking`, not `runTest`'s virtual clock.
   */
  @Test
  fun cancellingTheRunStopsTheScript() = runBlocking {
    val late = File(spaceDir, "late")
    val job = launch {
      shell()
        .run("base", "sleep 2; echo late > \"\$FAKE_PROOT_ROOT/late\"", 1.minutes)
        .collect { }
    }
    delay(200)
    job.cancelAndJoin()

    val wroteLate = withTimeoutOrNull(3_000) {
      while (!late.exists()) delay(50)
      true
    } ?: false
    assertFalse("the script outlived its cancelled run", wroteLate)
  }
}
