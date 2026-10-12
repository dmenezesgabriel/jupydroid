package com.jupydroid.app.data.space

import com.jupydroid.app.data.disk.linkTakenBeforeTheWrite
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

  /**
   * The kernel is the image's own interpreter, named inside the root the server runs under
   * (ADR-0073): the same `/usr/bin/python3` the server itself was started as.
   */
  @Test
  fun install_pointsArgvAtTheImagesOwnInterpreter() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir)

    assertEquals(SPACE_INTERPRETER, argv(baseDir).first())
  }

  /** A kernel death with no traceback is the failure mode we refuse to ship. */
  @Test
  fun install_keepsFaulthandlerOnSoAKernelCrashIsNeverSilent() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir)

    assertTrue(kernelJson(baseDir).readText().contains("PYTHONFAULTHANDLER"))
  }

  @Test
  fun install_launchesIpykernelWithTheConnectionFilePlaceholder() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir)

    val argv = kernelJson(baseDir).readText()
    assertTrue(argv, argv.contains("jupydroid_kernel"))
    assertTrue(argv, argv.contains("{connection_file}"))
  }

  /** `<sys.prefix>/share/jupyter`, the kernels directory the image's own Jupyter searches. */
  @Test
  fun install_writesWhereJupyterActuallySearches() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir)

    assertTrue(File(baseDir, "usr/share/jupyter/kernels/python3/kernel.json").isFile)
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

    SpaceKernelSpec.install(baseDir)

    val argv = argv(baseDir)
    assertTrue(argv.toString(), argv.contains("--log-level=DEBUG"))
  }

  /**
   * The env's interpreter runs the image's kernel module by path. A venv cannot import it - it is in
   * the system site-packages - and `run_path` adds nothing to `sys.path`, where running the file as a
   * script would put that whole directory on it and every system package with it.
   */
  @Test
  fun installEnvironment_runsTheImagesKernelModuleUnderTheEnvironmentsInterpreter() = runTest {
    val spaceDir = tempFolder.newFolder("space-1")

    SpaceKernelSpec.installEnvironment(spaceDir, "stats", "/home/jupydroid/envs/stats/bin/python", "Python (stats)")

    val spec = Json.parseToJsonElement(File(spaceDir, "usr/local/share/jupyter/kernels/stats/kernel.json").readText())
      .jsonObject
    assertEquals(
      listOf(
        "/home/jupydroid/envs/stats/bin/python",
        "-c",
        "import runpy; runpy.run_path('/usr/lib/python3.12/site-packages/jupydroid_kernel.py', run_name='__main__')",
        "-f",
        "{connection_file}",
        "--log-level=DEBUG",
      ),
      spec.getValue("argv").jsonArray.map { it.jsonPrimitive.content },
    )
    assertEquals("Python (stats)", spec.getValue("display_name").jsonPrimitive.content)
    assertTrue(spec.toString(), spec.toString().contains("PYTHONFAULTHANDLER"))
  }

  /**
   * The spec is written on every launch, so the write that matters is the one over a spec already
   * there: it replaces the file whole, and a kill mid-write leaves the old spec rather than half of
   * one, which would leave the Space with no kernel to start (ADR-0089).
   */
  @Test
  fun install_replacesTheSpecRatherThanRewritingIt() = runTest {
    val baseDir = tempFolder.newFolder("base")
    kernelJson(baseDir).apply { parentFile?.mkdirs() }.writeText("""{"argv":["python"]}""")
    val taken = kernelJson(baseDir).linkTakenBeforeTheWrite(tempFolder.root)

    SpaceKernelSpec.install(baseDir)

    assertEquals("""{"argv":["python"]}""", taken.readText())
  }

  /** A registered environment's spec is replaced whole too: half of one loses that kernel (ADR-0089). */
  @Test
  fun installEnvironment_replacesTheSpecRatherThanRewritingIt() = runTest {
    val spaceDir = tempFolder.newFolder("space-1")
    SpaceKernelSpec.installEnvironment(spaceDir, "stats", "/home/jupydroid/envs/stats/bin/python", "Python (stats)")
    val taken = File(spaceDir, "usr/local/share/jupyter/kernels/stats/kernel.json")
      .linkTakenBeforeTheWrite(tempFolder.root)
    val before = taken.readText()

    SpaceKernelSpec.installEnvironment(spaceDir, "stats", "/home/jupydroid/envs/stats/bin/python", "Stats")

    assertEquals(before, taken.readText())
  }

  private fun argv(baseDir: File): List<String> = Json.parseToJsonElement(kernelJson(baseDir).readText())
    .jsonObject.getValue("argv").jsonArray.map { it.jsonPrimitive.content }

  private fun kernelJson(baseDir: File) = File(baseDir, "usr/share/jupyter/kernels/python3/kernel.json")
}
