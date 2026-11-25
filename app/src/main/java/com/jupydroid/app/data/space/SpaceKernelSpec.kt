package com.jupydroid.app.data.space

import com.jupydroid.app.data.disk.writeTextAtomically
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
 * Points the Space's `python3` kernelspec at an interpreter and our kernel module.
 *
 * The default spec runs the gnu payload's CPython ([GNU_INTERPRETER]) while the server stays
 * musl: only the kernel is glibc, so `%pip` gets PyPI's manylinux wheels (ADR-0166). Its env is
 * [DEFAULT_KERNEL_ENV] - jupyter_client merges a spec's env over the server's
 * (`jupyter_client/provisioning/provisioner_base.py` in the pinned rootfs), which is how the
 * kernel alone gets the glibc half of the refusal split. A registered environment's spec keeps
 * running the venv's own interpreter over the image's kernel module ([installEnvironment]).
 *
 * Written over the shipped kernelspec in `usr/share/jupyter/kernels/`, which is
 * `<sys.prefix>/share/jupyter`, a directory jupyter_core searches.
 *
 * Both replace the spec whole rather than rewriting it where it sits (ADR-0089): half a spec is a
 * kernel the server cannot start, and a registered environment's spec is written once, so nothing
 * would put it back.
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
  internal const val ENVIRONMENT_KERNELS_PATH = "usr/local/share/jupyter/kernels"

  /**
   * The env the default kernel's spec carries, merged over the server's by jupyter_client. The
   * glibc half of the refusal split (ADR-0166): `LD_PRELOAD` is emptied rather than omitted so the
   * musl preloads never enter a glibc process; `PYTHONUSERBASE` and `PATH` give `%pip` a user base
   * no musl wheel can contaminate; `PROOT_SIGSYS_ENOSYS` is what the patched tracer reads from
   * the tracee's own environment to answer every seccomp trap with ENOSYS. `PATH` keeps `/pkgs/bin`
   * after the gnu base, so a musl-only console script still runs under its own interpreter.
   */
  internal val DEFAULT_KERNEL_ENV = mapOf(
    "PYTHONFAULTHANDLER" to "1",
    "LD_PRELOAD" to "",
    "PYTHONUSERBASE" to "/$SPACE_GNU_PKGS_DIR",
    "PROOT_SIGSYS_ENOSYS" to "1",
    "PATH" to
      "/$SPACE_GNU_PKGS_DIR/bin:/$SPACE_PKGS_DIR/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
  )

  /** A musl venv kernel stays musl: it inherits the server's preloads and handler, needing only faulthandler. */
  private val ENVIRONMENT_KERNEL_ENV = mapOf("PYTHONFAULTHANDLER" to "1")

  /** Written on every launch, so a Space extracted by an older version gets today's argv. */
  suspend fun install(baseDir: File, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) = withContext(ioDispatcher) {
    val kernelJson = File(baseDir, KERNEL_PATH)
    kernelJson.parentFile?.mkdirs()
    kernelJson.writeTextAtomically(
      kernelSpecJson(listOf(GNU_INTERPRETER, "-m", KERNEL_MODULE), DEFAULT_KERNEL_DISPLAY_NAME, DEFAULT_KERNEL_ENV),
    )
  }

  /**
   * Writes the spec [name] into the Space at [spaceDir], starting a kernel under [interpreter] - an
   * environment's own, as a process in the Space names it - so `sys.prefix` is that environment.
   *
   * [isGnu] picks the kernel's environment: a glibc interpreter gets [DEFAULT_KERNEL_ENV], the
   * glibc half of the refusal split (ADR-0166) - no musl preload may enter it and the tracer
   * answers its seccomp traps with ENOSYS - while a musl one keeps [ENVIRONMENT_KERNEL_ENV] and
   * inherits the server's own.
   *
   * ```
   * SpaceKernelSpec.installEnvironment(spaceDir, "stats", "/home/jupydroid/envs/stats/bin/python",
   *   "Python (stats)", isGnu = false)
   * ```
   */
  suspend fun installEnvironment(
    spaceDir: File,
    name: String,
    interpreter: String,
    displayName: String,
    isGnu: Boolean,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  ) = withContext(ioDispatcher) {
    val kernelJson = File(spaceDir, "$ENVIRONMENT_KERNELS_PATH/$name/kernel.json")
    kernelJson.parentFile?.mkdirs()
    kernelJson.writeTextAtomically(
      kernelSpecJson(
        listOf(interpreter, "-c", RUN_KERNEL_MODULE_BY_PATH),
        displayName,
        if (isGnu) DEFAULT_KERNEL_ENV else ENVIRONMENT_KERNEL_ENV,
      ),
    )
  }

  private fun kernelSpecJson(launch: List<String>, displayName: String, env: Map<String, String>): String =
    buildJsonObject {
      putJsonArray("argv") { (launch + listOf("-f", "{connection_file}", KERNEL_LOG_LEVEL)).forEach(::add) }
      put("display_name", displayName)
      put("language", "python")
      putJsonObject("env") { env.forEach { (name, value) -> put(name, value) } }
      putJsonObject("metadata") { put("debugger", true) }
    }.toString()
}
