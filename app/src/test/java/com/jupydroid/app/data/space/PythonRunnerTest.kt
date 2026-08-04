package com.jupydroid.app.data.space

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs against the host's `/bin/sh` standing in for the Space interpreter: this class
 * exists to launch a process and collect what it wrote, so faking the process would
 * leave nothing under test. [SpaceRuntime] only ever treats the interpreter as
 * `argv[0]`, which makes the substitution honest rather than a trick.
 */
class PythonRunnerTest {
  private val runtime = SpaceRuntime(
    baseDir = File("/nonexistent/spaces/base"),
    nativeInterpreter = File("/bin/sh"),
  )

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

  /**
   * The reason this parameter exists is `LD_PRELOAD`: naming the syscall that kills
   * numpy needs a SIGSYS handler preloaded into the Space process (evidence/step8.7).
   * The assertion deliberately uses an inert variable instead — a real `LD_PRELOAD`
   * would make the host's dynamic loader a participant in the test.
   */
  @Test
  fun run_passesExtraEnvironmentToTheProcess() = runTest {
    val runner = PythonRunner(runtime, extraEnv = mapOf("JUPYDROID_PROBE" to "libsigsys.so"))

    val outcome = runner.run("-c", "echo \$JUPYDROID_PROBE")

    assertEquals("libsigsys.so", outcome.output)
  }

  @Test
  fun run_keepsTheSpaceEnvironmentAlongsideTheExtras() = runTest {
    val runner = PythonRunner(runtime, extraEnv = mapOf("JUPYDROID_PROBE" to "libsigsys.so"))

    val outcome = runner.run("-c", "echo \$PYTHONHOME")

    assertEquals("/nonexistent/spaces/base/usr", outcome.output)
  }
}
