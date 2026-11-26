package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.disk.DiskKernelSpecs
import com.jupydroid.app.data.disk.DiskSpaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes

/**
 * **`jupydroid-env register` makes an existing interpreter or environment a kernelspec, with no
 * env create** - kernel creation as its own act, the `register` verb beside `create` in the same
 * helper `installOnStartOf` installs at `/usr/local/bin`. Run through the real proot `SpaceShell`
 * with the literal command the panel's port sends - `SpaceShellKernelRegistrationTest` pins the
 * adapter to that same text, so the two ends cannot drift - and through `runInSpace` for the
 * terminal's own route, where the question lives: the kernelspec search path, the libc env split
 * and `import ipykernel` all exist only on the device.
 *
 * The drift pin is the point: a spec the script writes must be the one
 * `SpaceKernelSpec.installEnvironment` writes for the same interpreter - one shape, two writers -
 * and an env's name must be `PythonEnvironment.kernelName`'s.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.KernelRegisterDeviceTest
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, so there is no Space elsewhere.
 */
@RunWith(AndroidJUnit4::class)
class KernelRegisterDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val spaceDir by lazy { baseSpaceDir(context) }
  private val dirs by lazy { SpaceDirs(spaceDir) }
  private val kernelSpecs by lazy { DiskKernelSpecs(spacesDir(context)) }
  private val shell by lazy {
    val repository = DiskSpaceRepository(spacesDir(context), spaceImageInstaller(context))
    spaceShell(context, repository) ?: error("spaceShell is null on arm64")
  }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking {
      baseSpaceInstaller(context).installIfNeeded()
      // The APK's own script, explicit: `runInSpace` runs no install, and a Space started by an
      // older APK carries that APK's helper.
      JupydroidEnvHelper.install(spaceDir, context.assets::open)
    }
  }

  @After
  fun removeWhatARegisterMade() {
    FIXTURE_ENVS.forEach { File(dirs.workspace, it).deleteRecursively() }
    REGISTERED_NAMES.forEach { File(spaceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$it").deleteRecursively() }
    File(dirs.tmp, REFERENCE_DIR).deleteRecursively()
  }

  /**
   * The Space's own gnu interpreter, registered with no environment at all: the spec is the glibc
   * one `installEnvironment` writes - a bare `/opt/gnu/...` path picks the libc like `pyvenv.cfg`
   * `home` does for an env.
   */
  @Test(timeout = 300_000)
  fun register_registersTheGnuInterpreterAsAKernel() = runBlocking {
    val events = register(GNU_INTERPRETER).toList()
    Log.i(
      SPACE_LOG_TAG,
      "register $GNU_INTERPRETER: ${events.outputLines().joinToString(" | ")}; last=${events.last()}",
    )

    assertEquals(SpaceShellEvent.Finished(0), events.last())
    assertSpecWritten(GNU_KERNEL_NAME, GNU_INTERPRETER, "Python ($GNU_INTERPRETER)", isGnu = true)
  }

  /** The musl interpreter gets the musl spec env - the server's own, not the glibc split. */
  @Test(timeout = 300_000)
  fun register_registersTheMuslInterpreterAsAKernel() = runBlocking {
    val events = register(SPACE_INTERPRETER).toList()
    Log.i(SPACE_LOG_TAG, "register $SPACE_INTERPRETER: last=${events.last()}")

    assertEquals(SpaceShellEvent.Finished(0), events.last())
    assertSpecWritten(MUSL_KERNEL_NAME, SPACE_INTERPRETER, "Python ($SPACE_INTERPRETER)", isGnu = false)
  }

  /**
   * An environment directory - and the same env named by its `bin/python`, which must land on the
   * one name `PythonEnvironment.kernelName` gives the env, so both spellings register one kernel.
   */
  @Test(timeout = 300_000)
  fun register_registersAnEnvironmentByDirectoryAndByItsInterpreter() = runBlocking {
    val environment = PythonEnvironment(ENV_PATH, hasIpykernel = true, isGnu = true)
    val interpreter = makeEnv(ENV_PATH, home = "/opt/gnu/python/bin", withSystemSite = true)

    val byDir = register(ENV_PATH).toList()
    Log.i(SPACE_LOG_TAG, "register $ENV_PATH: last=${byDir.last()}")
    assertEquals(SpaceShellEvent.Finished(0), byDir.last())
    assertSpecWritten(environment.kernelName, interpreter, environment.kernelDisplayName, isGnu = true)

    val again = register("$ENV_PATH/bin/python").toList()
    assertTrue(
      "registering the same env by its interpreter must refuse the held name: ${again.last()}",
      (again.last() as SpaceShellEvent.Finished).exitCode != 0,
    )
    assertTrue(
      "the refusal must name what holds it: ${again.outputLines()}",
      again.outputLines().any { environment.kernelName in it && "held" in it },
    )
  }

  /**
   * An env spelled by its interpreter alone converges to the env's name: `envs/spelled`'s
   * `bin/python` is `envs/spelled`'s, not a bare interpreter named `…-bin-python`.
   */
  @Test(timeout = 300_000)
  fun register_namesAnEnvSpelledByItsInterpreterAfterTheEnvironment() = runBlocking {
    val environment = PythonEnvironment(SPELLED_ENV_PATH, hasIpykernel = true, isGnu = true)
    val interpreter = makeEnv(SPELLED_ENV_PATH, home = "/opt/gnu/python/bin", withSystemSite = true)

    val events = register("$SPELLED_ENV_PATH/bin/python").toList()
    Log.i(SPACE_LOG_TAG, "register $SPELLED_ENV_PATH/bin/python: last=${events.last()}")

    assertEquals(SpaceShellEvent.Finished(0), events.last())
    assertSpecWritten(environment.kernelName, interpreter, environment.kernelDisplayName, isGnu = true)
  }

  /**
   * The refusals are the script's own, reached through the same command the port sends: an env
   * without ipykernel is named with the command that adds it, a bare directory is no environment,
   * a missing path is nothing at all, and the verb takes exactly one argument.
   */
  @Test(timeout = 300_000)
  fun register_refusesWhatItCannotRegister() = runBlocking {
    makeEnv(MUSL_ENV_PATH, home = "/usr/bin", withSystemSite = false)
    val noIpykernel = register(MUSL_ENV_PATH).toList()
    Log.i(SPACE_LOG_TAG, "register $MUSL_ENV_PATH (no ipykernel): ${noIpykernel.outputLines().lastOrNull()}")
    assertTrue(
      "an env with no ipykernel must refuse: ${noIpykernel.last()}",
      (noIpykernel.last() as SpaceShellEvent.Finished).exitCode != 0,
    )
    assertTrue(
      "the refusal must carry the command that adds ipykernel: ${noIpykernel.outputLines()}",
      noIpykernel.outputLines().any { "pip" in it && "ipykernel" in it },
    )

    File(dirs.workspace, NOT_AN_ENV).mkdirs()
    val bare = register(NOT_AN_ENV).toList()
    assertTrue(
      "a directory without pyvenv.cfg must refuse: ${bare.last()}",
      (bare.last() as SpaceShellEvent.Finished).exitCode != 0,
    )

    val missing = register("no/such/thing").toList()
    assertTrue(
      "a path that is nothing must refuse: ${missing.last()}",
      (missing.last() as SpaceShellEvent.Finished).exitCode != 0,
    )

    val noArgs = register().toList()
    assertEquals("register with no argument is a usage refusal", SpaceShellEvent.Finished(2), noArgs.last())
  }

  /**
   * The terminal route: `/usr/local/bin` is on the Space's PATH, so a person types the helper by
   * name - `python3` resolving through `command -v` proves the bare-name arm and the musl spec in
   * one run.
   */
  @Test(timeout = 300_000)
  fun theTerminalRegistersTheSameHelperByName() {
    val run = runInSpace(SPACE_SHELL, "-c", "jupydroid-env register python3")
    Log.i(SPACE_LOG_TAG, "jupydroid-env register python3: exit=${run.exitCode} ${run.output.trim()}")

    assertEquals(0, run.exitCode)
    assertSpecExists(MUSL_KERNEL_NAME)
  }

  /** The port's own command, sent exactly as `SpaceShellKernelRegistration` builds it. */
  private fun register(arg: String? = null) =
    shell.run(BASE_SPACE.id, "jupydroid-env register" + (arg?.let { " '$it'" } ?: ""), REGISTER_TIMEOUT)

  /**
   * A minimal env: `pyvenv.cfg` naming the interpreter's home and `bin/python` linked to it.
   * `include-system-site-packages` is what lets the fixture carry ipykernel with no pip - the
   * script checks `import ipykernel`, which the base interpreter's own site-packages satisfies.
   * Answers the interpreter as a process in the Space names it.
   */
  private fun makeEnv(path: String, home: String, withSystemSite: Boolean): String {
    val env = File(dirs.workspace, path)
    File(env, "bin").mkdirs()
    File(env, "pyvenv.cfg").writeText(
      "home = $home\ninclude-system-site-packages = ${if (withSystemSite) "true" else "false"}\n",
    )
    val target = if (home.startsWith("/opt/gnu/")) GNU_INTERPRETER else SPACE_INTERPRETER
    Files.createSymbolicLink(File(env, "bin/python").toPath(), Paths.get(target))
    return dirs.inRoot(env) + "/bin/python"
  }

  /**
   * The spec on disk is the one `installEnvironment` writes for the same interpreter, field for
   * field - the drift pin between the script's writer and the app's - and the catalogue offers it
   * under the display name.
   */
  private suspend fun assertSpecWritten(name: String, interpreter: String, display: String, isGnu: Boolean) {
    val referenceDir = File(dirs.tmp, "$REFERENCE_DIR/$name").apply { mkdirs() }
    SpaceKernelSpec.installEnvironment(referenceDir, name, interpreter, display, isGnu)
    val written = File(spaceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$name/kernel.json")
    val expected = File(referenceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$name/kernel.json")
    assertEquals(
      "the script's spec for $name differs from installEnvironment's",
      Json.parseToJsonElement(expected.readText()).jsonObject,
      Json.parseToJsonElement(written.readText()).jsonObject,
    )
    assertEquals(
      KernelSpec(name, display, "python"),
      kernelSpecs.specs(BASE_SPACE).specs.firstOrNull {
        it.name == name
      },
    )
  }

  private fun assertSpecExists(name: String) {
    assertTrue(
      "no kernelspec '$name' under ${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}",
      File(spaceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$name/kernel.json").isFile,
    )
  }

  private companion object {
    const val ENV_PATH = "envs/regfix"
    const val SPELLED_ENV_PATH = "envs/spellfix"
    const val MUSL_ENV_PATH = "envs/muslfix"
    const val NOT_AN_ENV = "envs/notanenv"
    const val GNU_KERNEL_NAME = "opt-gnu-python-bin-python3.12"
    const val MUSL_KERNEL_NAME = "usr-bin-python3"
    const val REFERENCE_DIR = "spec-reference"
    val REGISTERED_NAMES = listOf(GNU_KERNEL_NAME, MUSL_KERNEL_NAME, "regfix", "spellfix", "muslfix")
    val FIXTURE_ENVS = listOf(ENV_PATH, SPELLED_ENV_PATH, MUSL_ENV_PATH, NOT_AN_ENV)
    val REGISTER_TIMEOUT = 1.minutes
  }
}
