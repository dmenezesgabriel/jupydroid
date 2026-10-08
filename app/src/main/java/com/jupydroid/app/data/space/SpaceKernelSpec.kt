package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_KERNEL_DISPLAY_NAME
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Points the Space's `python3` kernelspec at the image's own interpreter and our kernel module.
 *
 * The kernelspec shipped in the image launches bare `python -m ipykernel_launcher`. Under proot the
 * image runs as built (ADR-0073), so argv[0] is [SPACE_INTERPRETER], the same `/usr/bin/python3`
 * the server was started as; the module is what this file exists to replace.
 *
 * Written over the shipped kernelspec in `usr/share/jupyter/kernels/`, which is
 * `<sys.prefix>/share/jupyter`, a directory jupyter_core searches.
 *
 * A registered environment's spec runs the same module under the environment's interpreter
 * ([installEnvironment]); both carry the same flags, environment and metadata.
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
   * separates the two causes of the execution stall.
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
   * The same module run by path, for an interpreter that cannot import it: a venv's `sys.path` lacks
   * the system site-packages the image puts it in (`tools/rootfs/Dockerfile`). `run_path` adds nothing
   * to `sys.path`, where running the file as a script would put that whole directory on it, and every
   * system package with it.
   */
  private const val RUN_KERNEL_MODULE_BY_PATH =
    "import runpy; runpy.run_path('/usr/lib/python3.12/site-packages/$KERNEL_MODULE.py', run_name='__main__')"

  /** Where a registered environment's spec goes: `<sys.prefix>/local/share/jupyter`, never the workspace. */
  private const val ENVIRONMENT_KERNELS_PATH = "usr/local/share/jupyter/kernels"

  /** Written on every launch, so a Space extracted by an older version gets today's argv. */
  suspend fun install(baseDir: File, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext(ioDispatcher) {
    val kernelJson = File(baseDir, KERNEL_PATH)
    kernelJson.parentFile?.mkdirs()
    kernelJson.writeText(kernelSpecJson(listOf(SPACE_INTERPRETER, "-m", KERNEL_MODULE), DEFAULT_KERNEL_DISPLAY_NAME))
  }

  /**
   * Writes the spec [name] into the Space at [spaceDir], starting a kernel under [interpreter] - an
   * environment's own, as a process in the Space names it - so `sys.prefix` is that environment.
   *
   * ```
   * SpaceKernelSpec.installEnvironment(spaceDir, "stats", "/home/jupydroid/envs/stats/bin/python", "Python (stats)")
   * ```
   */
  suspend fun installEnvironment(
    spaceDir: File,
    name: String,
    interpreter: String,
    displayName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  ) = withContext(ioDispatcher) {
    val kernelJson = File(spaceDir, "$ENVIRONMENT_KERNELS_PATH/$name/kernel.json")
    kernelJson.parentFile?.mkdirs()
    kernelJson.writeText(kernelSpecJson(listOf(interpreter, "-c", RUN_KERNEL_MODULE_BY_PATH), displayName))
  }

  private fun kernelSpecJson(launch: List<String>, displayName: String): String = buildJsonObject {
    putJsonArray("argv") { (launch + listOf("-f", "{connection_file}", KERNEL_LOG_LEVEL)).forEach(::add) }
    put("display_name", displayName)
    put("language", "python")
    putJsonObject("env") { put("PYTHONFAULTHANDLER", "1") }
    putJsonObject("metadata") { put("debugger", true) }
  }.toString()
}
