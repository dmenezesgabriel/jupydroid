package com.jupydroid.app.data.space

import java.io.File

/**
 * **How a process runs inside a Space's private root: under proot, with [root] as `/`** (ADR-0073).
 *
 * proot is a `nativeLibraryDir` ELF because at targetSdk 36 nothing under `filesDir` may be
 * `execve`d. For the same reason its loader ships beside it and [environment] names it in
 * `PROOT_LOADER`: unset, proot writes its embedded loader under `PROOT_TMP_DIR`, which is
 * `filesDir`, and execs it from there. `-0` reports uid 0, which `apk add` needs.
 *
 * ```
 * val proot = ProotCommand(File(applicationInfo.nativeLibraryDir), spaceDir)
 * ProcessBuilder(proot.argv(listOf("/bin/sh", "-c", "true")))
 *   .apply { environment().putAll(proot.environment()) }
 * ```
 */
data class ProotCommand(val nativeLibraryDir: File, val root: File) {
  /** [command], named by its in-root path, run with [root] as `/` and `/dev` and `/proc` bound through. */
  fun argv(command: List<String>): List<String> = listOf(
    File(nativeLibraryDir, PROOT).path,
    "-r",
    root.path,
    "-0",
    "-w",
    "/",
    "-b",
    "/dev",
    "-b",
    "/proc",
  ) + command

  /** What proot itself reads, as opposed to what the process it runs reads. */
  fun environment(): Map<String, String> = mapOf(
    "PROOT_LOADER" to File(nativeLibraryDir, PROOT_LOADER).path,
    "PROOT_TMP_DIR" to File(root, "tmp").path,
  )

  companion object {
    /** Both staged into arm64 `jniLibs` by `scripts/stage_proot.py`. */
    const val PROOT = "libproot.so"
    const val PROOT_LOADER = "libproot-loader.so"
  }
}
