package com.jupydroid.app.data.space

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * Points the rootfs's shell and coreutils at the busybox that can actually be executed.
 *
 * The image ships **one** real ELF, `bin/busybox`, and **304** applet symlinks - `bin/sh`,
 * `bin/ls`, `usr/bin/awk` and the rest - every one of them the same *absolute*
 * `-> /bin/busybox`. On device that resolves against Android's root rather than the Space, so all
 * 304 dangle; and the ELF underneath them could not be `execve`'d out of `filesDir` even if they
 * did (ADR-0002).
 *
 * **Repairing `bin/sh` alone is not enough, and the obvious gate hides it.** A shell whose `ls`,
 * `cat`, `grep` and `vi` are all *not found* still answers `sh -c 'echo ok'`, because `echo` is an
 * ash builtin. The naming constraint is the one ADR-0045 records: busybox reads its applet from the
 * **basename** of the path it is reached by, and its `argv[1]` fallback engages only for a basename
 * prefixed `busybox` - which `libbusybox.so` is not.
 *
 * **One maintained link rather than 305.** `bin/busybox` points into `nativeLibraryDir`, which
 * moves on every app update; the applets point *relatively* at `bin/busybox`, so they can never go
 * stale and only the one link has to be repaired. `execve` walks the chain to find the inode and
 * runs the SELinux check on the target, while busybox reads `ls` off the path the caller gave it.
 *
 * ```
 * BusyBoxApplets(baseDir, File(nativeLibraryDir, "libbusybox.so")).relink()
 * ```
 */
class BusyBoxApplets(
  private val baseDir: File,
  private val nativeBusyBox: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  /**
   * Repoints `bin/busybox` and returns how many applets were made relative.
   *
   * Idempotent, and run on every launch for the reason the loader link is: an applet already
   * relative no longer matches [SHIPPED_TARGET] and is skipped, so a second pass is a walk that
   * changes nothing.
   */
  suspend fun relink(): Int = withContext(ioDispatcher) {
    pointBusyBoxAtNativeLibrary()
    APPLET_DIRS.sumOf { relativiseAppletsIn(File(baseDir, it)) }
  }

  private fun pointBusyBoxAtNativeLibrary() {
    val busybox = File(baseDir, BUSYBOX_PATH)
    busybox.parentFile?.mkdirs()
    busybox.delete()
    Files.createSymbolicLink(busybox.toPath(), nativeBusyBox.toPath())
  }

  /** Only links pointing where the image left them; `etc/mtab -> /proc/mounts` is not ours. */
  private fun relativiseAppletsIn(dir: File): Int = dir.listFiles().orEmpty().count { applet ->
    Files.isSymbolicLink(applet.toPath()) &&
      Files.readSymbolicLink(applet.toPath()).toString() == SHIPPED_TARGET &&
      applet.relinkTo(dir.toPath().relativize(File(baseDir, BUSYBOX_PATH).toPath()).toString())
  }

  private fun File.relinkTo(target: String): Boolean {
    delete()
    Files.createSymbolicLink(toPath(), File(target).toPath())
    return true
  }

  private companion object {
    const val BUSYBOX_PATH = "bin/busybox"

    /** What every applet link in the image says, and the only target this class claims. */
    const val SHIPPED_TARGET = "/bin/busybox"

    /** Where Alpine puts them; `tar -tzf` finds applet links in these four and nowhere else. */
    val APPLET_DIRS = listOf("bin", "sbin", "usr/bin", "usr/sbin")
  }
}
