package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs against the host's `/bin/sh` standing in for the Space interpreter, under [FakeProotScript]:
 * this class exists to launch a process and collect what it wrote, so faking the process would
 * leave nothing under test. [SpaceRuntime] only ever treats the interpreter as the program proot
 * runs, which makes the substitution honest rather than a trick.
 */
class PythonRunnerTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val runtime by lazy {
    val space = tempFolder.newFolder("base").apply {
      FakeProotScript.program(this, SPACE_INTERPRETER, "exec /bin/sh \"\$@\"")
      SpaceDirs(this).ensure()
    }
    SpaceRuntime(spaceDir = space, nativeLibraryDir = FakeProotScript.installIn(tempFolder.newFolder("native")))
  }

  @Test
  fun run_collectsOutputAndExitCode() = runTest {
    val outcome = PythonRunner(runtime).run("-c", "echo hello; exit 3")

    assertEquals("hello", outcome.output)
    assertEquals(3, outcome.exitCode)
    assertTrue("exit 3 is not success", !outcome.succeeded)
  }

  /** `redirectErrorStream`: a traceback is worthless if it arrives detached from the output. */
  @Test
  fun run_foldsStderrIntoTheSameStream() = runTest {
    val outcome = PythonRunner(runtime).run("-c", "echo out; echo err >&2")

    assertEquals(setOf("out", "err"), outcome.output.lines().toSet())
  }

  /** The Space environment must reach the process, not just the command line. */
  @Test
  fun run_launchesTheProcessWithTheSpaceEnvironment() = runTest {
    val outcome = PythonRunner(runtime).run("-c", "echo \$HOME")

    assertEquals("/home/jupydroid", outcome.output)
  }
}
