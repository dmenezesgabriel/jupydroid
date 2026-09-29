package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpaceKernelSpecTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val interpreter = File("/data/app/abc==/lib/arm64/libpython3.so")

  /**
   * The whole point: the shipped kernelspec runs bare "python", which PATH resolves
   * into the rootfs, and rootfs files cannot be execve'd. argv[0] has to be the
   * interpreter in nativeLibraryDir.
   */
  @Test
  fun install_pointsArgvAtTheExecutableInterpreter() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir, interpreter)

    assertTrue(kernelJson(baseDir).readText().contains("\"${interpreter.path}\""))
  }

  /** A kernel death with no traceback is the failure mode we refuse to ship. */
  @Test
  fun install_keepsFaulthandlerOnSoAKernelCrashIsNeverSilent() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir, interpreter)

    assertTrue(kernelJson(baseDir).readText().contains("PYTHONFAULTHANDLER"))
  }

  @Test
  fun install_launchesIpykernelWithTheConnectionFilePlaceholder() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir, interpreter)

    val argv = kernelJson(baseDir).readText()
    assertTrue(argv, argv.contains("jupydroid_kernel"))
    assertTrue(argv, argv.contains("{connection_file}"))
  }

  /** The only kernels directory Jupyter searches when PYTHONHOME points into the Space. */
  @Test
  fun install_writesWhereJupyterActuallySearches() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir, interpreter)

    assertTrue(File(baseDir, "usr/share/jupyter/kernels/python3/kernel.json").isFile)
  }

  /** nativeLibraryDir moves on every app update, so a stale argv must be replaced. */
  @Test
  fun install_rewritesAStaleInterpreterPath() = runTest {
    val baseDir = tempFolder.newFolder("base")
    SpaceKernelSpec.install(baseDir, File("/data/app/old==/lib/arm64/libpython3.so"))

    SpaceKernelSpec.install(baseDir, interpreter)

    val written = kernelJson(baseDir).readText()
    assertTrue(written, written.contains(interpreter.path))
    assertEquals(false, written.contains("/data/app/old=="))
  }

  /**
   * Tells "the request never reached the kernel" from "the kernel received it and never
   * replied" - two causes with two different fixes, and no sighting of the execution stall
   * has ever been able to tell them apart.
   *
   * `--log-level` is traitlets' own alias for `Application.log_level`
   * (`traitlets/config/application.py`, `aliases = {"log-level": "Application.log_level"}`), and
   * at DEBUG `dispatch_shell` logs `*** MESSAGE TYPE:%s***` for every message it dispatches
   * (`ipykernel/kernelbase.py:455`) - on the shell channel, which is the one that stalls.
   *
   * Asserted on the parsed `argv` rather than on the file's text: a flag that landed in
   * `display_name` would satisfy a substring and configure nothing.
   */
  @Test
  fun install_asksTheKernelToReportEveryShellMessageItDispatches() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir, interpreter)

    val argv = argv(baseDir)
    assertTrue(argv.toString(), argv.contains("--log-level=DEBUG"))
  }

  private fun argv(baseDir: File): List<String> = Json.parseToJsonElement(kernelJson(baseDir).readText())
    .jsonObject.getValue("argv").jsonArray.map { it.jsonPrimitive.content }

  private fun kernelJson(baseDir: File) = File(baseDir, "usr/share/jupyter/kernels/python3/kernel.json")
}
