package com.jupydroid.app.data.space

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Points the Space's `python3` kernelspec at an interpreter that can actually be run.
 *
 * The kernelspec shipped in the image launches bare `python`, which PATH resolves
 * inside the rootfs - and a rootfs file cannot be execve'd at targetSdk 36. A
 * kernelspec is just JSON we own, so argv[0] becomes the interpreter in
 * nativeLibraryDir, which is the one place execution is permitted
 * (docs/PLAN.md, "Exec model").
 *
 * Written over the shipped kernelspec in `usr/share/jupyter/kernels/`. The tidier
 * `usr/local/share/...` override directory is not an option here: jupyter_core builds
 * that search path from an absolute `/usr/local/share/jupyter`, which does not exist
 * on the device. With `PYTHONHOME=<base>/usr` the only directory actually searched is
 * `<sys.prefix>/share/jupyter`, verified against GET /api/kernelspecs.
 */
object SpaceKernelSpec {
  private const val KERNEL_PATH = "usr/share/jupyter/kernels/python3/kernel.json"

  /**
   * Our entry point rather than `ipykernel_launcher`: stock ipykernel aborts on
   * Android because its IOPub subprocess pipe binds TCP. Shipped in the image, see
   * `tools/rootfs/jupydroid_kernel.py` for the full reasoning.
   */
  private const val KERNEL_MODULE = "jupydroid_kernel"

  /**
   * Rewritten on every launch, not just on a fresh install: nativeLibraryDir carries
   * per-install path segments, so after an app update a baked argv points at a
   * directory that no longer exists.
   */
  suspend fun install(
    baseDir: File,
    interpreter: File,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  ) = withContext(ioDispatcher) {
    val kernelJson = File(baseDir, KERNEL_PATH)
    kernelJson.parentFile?.mkdirs()
    kernelJson.writeText(kernelSpecJson(interpreter))
  }

  private fun kernelSpecJson(interpreter: File): String =
    """
    {
      "argv": ["${interpreter.path}", "-m", "$KERNEL_MODULE", "-f", "{connection_file}"],
      "display_name": "Python 3",
      "language": "python",
      "env": {"PYTHONFAULTHANDLER": "1"},
      "metadata": {"debugger": true}
    }
    """.trimIndent()
}
