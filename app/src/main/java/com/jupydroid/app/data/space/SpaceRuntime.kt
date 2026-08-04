package com.jupydroid.app.data.space

import java.io.File

/**
 * The command line and environment for running a Space's Python.
 *
 * Two things here are not obvious and both are load-bearing:
 *
 * 1. The interpreter is exec'd from [nativeInterpreter], not from `usr/bin/python3`
 *    inside the rootfs. Only the APK native library directory carries a SELinux
 *    context that permits execve at targetSdk 36 (docs/PLAN.md, "Exec model").
 *    A pleasant side effect is that `sys.executable` is then a path the kernel
 *    subprocess can be launched from too, so kernelspecs need no special casing.
 * 2. `PYTHONHOME` is required, because Python derives its prefix from `argv[0]` and
 *    that now sits in the native library directory, nowhere near the stdlib.
 *
 * ```
 * SpaceRuntime(baseDir, nativeInterpreter).command("-m", "jupyter_server")
 * ```
 */
class SpaceRuntime(
  private val baseDir: File,
  private val nativeInterpreter: File,
) {
  fun command(vararg args: String): List<String> = listOf(nativeInterpreter.path) + args

  fun environment(): Map<String, String> = mapOf(
    "PYTHONHOME" to inBase("usr"),
    "HOME" to inBase("home/jupydroid"),
    "TMPDIR" to inBase("tmp"),
    "PATH" to listOf(inBase("usr/local/bin"), inBase("usr/bin"), inBase("bin")).joinToString(":"),
    // Inherited by dlopen'd extension modules, unlike the interpreter's DT_RUNPATH.
    // musl has no multiarch directories, so these two cover the whole rootfs.
    "LD_LIBRARY_PATH" to listOf(inBase("usr/lib"), inBase("lib")).joinToString(":"),
  )

  /**
   * A [ProcessBuilder] with Android's own environment replaced by the Space's.
   * Shared so the short-lived [PythonRunner] and the long-lived server launcher
   * cannot drift apart on how a Space process is set up.
   */
  fun processBuilder(args: List<String>, extraEnv: Map<String, String> = emptyMap()): ProcessBuilder =
    ProcessBuilder(command(*args.toTypedArray())).apply {
      // Android's PATH and linker variables would otherwise leak into the Space.
      environment().clear()
      environment().putAll(this@SpaceRuntime.environment() + extraEnv)
    }

  private fun inBase(relative: String): String = File(baseDir, relative).path
}
