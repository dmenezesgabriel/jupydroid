package com.jupydroid.app.data.space

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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

  @Test
  fun install_launchesIpykernelWithTheConnectionFilePlaceholder() = runTest {
    val baseDir = tempFolder.newFolder("base")

    SpaceKernelSpec.install(baseDir, interpreter)

    val argv = kernelJson(baseDir).readText()
    assertTrue(argv, argv.contains("ipykernel_launcher"))
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

  private fun kernelJson(baseDir: File) =
    File(baseDir, "usr/share/jupyter/kernels/python3/kernel.json")
}
