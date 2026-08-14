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
 * (docs/DECISIONS.md, "Exec model").
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
   * Makes the kernel say which shell messages it dispatched, which is the one thing that
   * separates the two causes of the execution stall (`docs/PLAN.md` P0).
   *
   * At DEBUG `dispatch_shell` logs `*** MESSAGE TYPE:%s***` per message
   * (`ipykernel/kernelbase.py:455`), so a stalled `execute_request` with the line present means
   * the kernel heard it and did not reply, and without it means the request never arrived. The
   * server cannot answer that question: its own nudge is satisfied by a *control* reply alone
   * (`jupyter_server/services/kernels/connection/channels.py:158-164`), so a dead shell channel
   * looks healthy to it.
   *
   * `--log-level` is traitlets' alias for `Application.log_level`, and the output lands in
   * `run/jupyter_server.log`: the kernel inherits the server's stderr
   * (`jupyter_client/launcher.py:68-76` leaves it `None` off Windows) and ipykernel re-points a
   * logger bound to fd 2 at the raw descriptor rather than at the iopub stream
   * (`ipykernel/kernelapp.py:546-554`), so it cannot reach a cell's output.
   *
   * Drop it with `--ServerApp.log_level=DEBUG` when the app is feature-complete, not before.
   */
  private const val KERNEL_LOG_LEVEL = "--log-level=DEBUG"

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
      "argv": ["${interpreter.path}", "-m", "$KERNEL_MODULE", "-f", "{connection_file}", "$KERNEL_LOG_LEVEL"],
      "display_name": "Python 3",
      "language": "python",
      "env": {"PYTHONFAULTHANDLER": "1"},
      "metadata": {"debugger": true}
    }
    """.trimIndent()
}
