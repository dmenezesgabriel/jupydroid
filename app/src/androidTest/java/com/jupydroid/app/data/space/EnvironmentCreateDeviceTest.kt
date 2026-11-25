package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.disk.DiskKernelSpecs
import com.jupydroid.app.data.disk.DiskSpaceEnvironments
import com.jupydroid.app.data.disk.DiskSpaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSpec
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

/**
 * **`jupydroid-env create` makes a project's `.venv` and registers its kernel, end to end** -
 * the script asset installed at `/usr/local/bin` by `installOnStartOf`, run through the real
 * proot `SpaceShell` the way the app's create asks it, proven where no JVM test can stand in:
 * the venv, the pip install and the kernelspec search path all live on the device.
 *
 * The drift pin is the point of the second half: the spec the script writes must be the one
 * `SpaceKernelSpec.installEnvironment` writes for the same env - one shape, two writers - so a
 * kernel create registers is the kernel Register would have given a hand-made one.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.EnvironmentCreateDeviceTest
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and the create's pip needs the tablet online.
 */
@RunWith(AndroidJUnit4::class)
class EnvironmentCreateDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val spaceDir by lazy { baseSpaceDir(context) }
  private val dirs by lazy { SpaceDirs(spaceDir) }
  private val environments by lazy { DiskSpaceEnvironments(spacesDir(context)) }
  private val kernelSpecs by lazy { DiskKernelSpecs(spacesDir(context)) }
  private val creation by lazy {
    val repository = DiskSpaceRepository(spacesDir(context), spaceImageInstaller(context))
    SpaceShellEnvironmentCreation(spaceShell(context, repository) ?: error("spaceShell is null on arm64"))
  }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  @After
  fun removeWhatACreateMade() {
    File(dirs.workspace, PROJECT).deleteRecursively()
    File(spaceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$KERNEL_NAME").deleteRecursively()
    File(dirs.tmp, REFERENCE_DIR).deleteRecursively()
  }

  @Test(timeout = 900_000)
  fun createMakesTheProjectsVenvAndRegistersItsKernel() = runBlocking {
    File(dirs.workspace, PROJECT).mkdirs()

    val events = creation.create(BASE_SPACE.id, PROJECT).toList()
    Log.i(SPACE_LOG_TAG, "create $PROJECT: ${events.outputLines().joinToString(" | ")}; last=${events.last()}")

    assertEquals(SpaceShellEvent.Finished(0), events.last())
    assertEnvironmentMade()
    assertSpecMatchesRegister()
  }

  /** What discovery and the catalogue answer for the env the script made. */
  private suspend fun assertEnvironmentMade() {
    val found = environments.environments(BASE_SPACE.id).filter { it.path == "$PROJECT/.venv" }
    assertEquals("environments found: ${environments.environments(BASE_SPACE.id)}", 1, found.size)
    val environment = found.single()
    assertTrue("ipykernel not found in the venv's site-packages", environment.hasIpykernel)
    assertTrue("the venv was not made by the gnu interpreter", environment.isGnu)
    assertEquals(KERNEL_NAME, environment.registeredAs)

    val spec = kernelSpecs.specs(BASE_SPACE).specs.firstOrNull { it.name == KERNEL_NAME }
    assertEquals(KernelSpec(KERNEL_NAME, "Python ($PROJECT/.venv)", "python"), spec)
  }

  /**
   * The spec on disk is the one `installEnvironment` writes for the same env, field for field -
   * the drift pin between the script's writer and the app's.
   */
  private suspend fun assertSpecMatchesRegister() {
    val referenceDir = File(dirs.tmp, REFERENCE_DIR).apply { mkdirs() }
    SpaceKernelSpec.installEnvironment(
      referenceDir,
      KERNEL_NAME,
      "/home/jupydroid/$PROJECT/.venv/bin/python",
      "Python ($PROJECT/.venv)",
      isGnu = true,
    )
    val written = File(spaceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$KERNEL_NAME/kernel.json")
    val expected = File(referenceDir, "${SpaceKernelSpec.ENVIRONMENT_KERNELS_PATH}/$KERNEL_NAME/kernel.json")
    assertEquals(
      Json.parseToJsonElement(expected.readText()).jsonObject,
      Json.parseToJsonElement(written.readText()).jsonObject,
    )
  }

  /**
   * The refusals are the script's own, reached through the same port the panel will use: an
   * existing `.venv` and a missing project both fail before pip runs, so they are cheap arms.
   */
  @Test(timeout = 300_000)
  fun create_refusesWhatItCannotMake() = runBlocking {
    File(dirs.workspace, "$PROJECT/.venv").mkdirs()

    val again = creation.create(BASE_SPACE.id, PROJECT).toList()
    Log.i(SPACE_LOG_TAG, "re-create $PROJECT: last=${again.last()}; ${again.outputLines().lastOrNull()}")
    assertTrue("re-create did not refuse: ${again.last()}", (again.last() as SpaceShellEvent.Finished).exitCode != 0)
    assertTrue("the refusal must say why: ${again.outputLines()}", again.outputLines().any { "already exists" in it })

    val missing = creation.create(BASE_SPACE.id, "no-such-project").toList()
    assertTrue(
      "missing project did not refuse: ${missing.last()}",
      (missing.last() as SpaceShellEvent.Finished).exitCode != 0,
    )
  }

  /**
   * The terminal route: `/usr/local/bin` is on the Space's PATH, so the helper the panel's
   * `SpaceShell` ran is the same file a person types - usage and the already-exists refusal
   * prove the binary, its exec bit and the script's own logic without a second pip install.
   */
  @Test(timeout = 300_000)
  fun theTerminalRunsTheSameHelperByName() {
    val usage = runInSpace(SPACE_SHELL, "-c", "jupydroid-env")
    Log.i(SPACE_LOG_TAG, "jupydroid-env (no args): exit=${usage.exitCode} ${usage.output.trim()}")
    assertEquals(2, usage.exitCode)
    assertTrue(usage.output.contains("usage: jupydroid-env create"))

    File(dirs.workspace, "$PROJECT/.venv").mkdirs()
    val refused = runInSpace(SPACE_SHELL, "-c", "jupydroid-env create $PROJECT")
    Log.i(SPACE_LOG_TAG, "terminal re-create $PROJECT: exit=${refused.exitCode} ${refused.output.trim()}")
    assertTrue(
      "terminal create did not refuse the existing .venv: ${refused.output}",
      "already exists" in refused.output,
    )
  }

  private companion object {
    const val PROJECT = "proj-a"
    const val KERNEL_NAME = "proj-a-.venv"
    const val REFERENCE_DIR = "spec-reference"
  }
}
