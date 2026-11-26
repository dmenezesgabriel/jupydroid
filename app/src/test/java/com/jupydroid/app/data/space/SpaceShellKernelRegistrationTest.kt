package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.data.fake.SpaceShellRun
import com.jupydroid.app.domain.model.SpaceInterpreter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * The register act's shape, pinned where a UI test cannot see it: which script the Space's shell
 * is asked to run and the bound it gets, and the two interpreters a Space lists with the
 * registered state its spec catalogue holds.
 */
class SpaceShellKernelRegistrationTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val shell = FakeSpaceShell()
  private val registration by lazy { SpaceShellKernelRegistration(shell, tempFolder.root) }

  private val spaceDir: File get() = File(tempFolder.root, "space-1").apply { mkdirs() }

  @Test
  fun interpreters_listsTheSpacesTwoPythonsUnregistered() = runTest {
    spaceDir

    assertEquals(
      listOf(
        SpaceInterpreter(SPACE_INTERPRETER, isGnu = false),
        SpaceInterpreter(GNU_INTERPRETER, isGnu = true),
      ),
      registration.interpreters("space-1"),
    )
  }

  /** The spec's `argv[0]` is the answer, an interpreter's as an environment's. */
  @Test
  fun interpreters_marksOneRegisteredUnderTheSpecThatRunsIt() = runTest {
    SpaceKernelSpec.installEnvironment(
      spaceDir,
      "usr-bin-python3",
      SPACE_INTERPRETER,
      "Python (/usr/bin/python3)",
      isGnu = false,
    )

    val listed = registration.interpreters("space-1")

    assertEquals("usr-bin-python3", listed.single { it.path == SPACE_INTERPRETER }.registeredAs)
    assertEquals(null, listed.single { it.path == GNU_INTERPRETER }.registeredAs)
  }

  @Test
  fun register_runsTheRegisterVerbInTheSpaceBounded() = runTest {
    spaceDir
    registration.register("space-1", "/usr/bin/python3").toList()

    assertEquals(
      listOf(SpaceShellRun("space-1", "jupydroid-env register '/usr/bin/python3'", 2.minutes)),
      shell.runs,
    )
  }

  /** A relative path or a bare name reaches the script verbatim, quoted as one word. */
  @Test
  fun register_quotesTheInterpreterSoAPathCannotSplitTheCommand() = runTest {
    spaceDir
    registration.register("space-1", "envs/my env/bin/python").toList()

    assertEquals("jupydroid-env register 'envs/my env/bin/python'", shell.runs[0].script)
  }

  /** The flow is cold like `SpaceShell.run`'s: nothing is ensured or exec'd until it is collected. */
  @Test
  fun register_isColdNothingRunsUntilTheFlowIsCollected() {
    spaceDir
    registration.register("space-1", "/usr/bin/python3")

    assertEquals(emptyList<SpaceShellRun>(), shell.runs)
  }
}
