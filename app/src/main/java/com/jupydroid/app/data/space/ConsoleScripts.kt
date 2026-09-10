package com.jupydroid.app.data.space

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Makes `pip`, `jupyter`, `ipython` and 28 other Python console scripts runnable by name.
 *
 * The image ships them in `usr/bin` as `#!/usr/bin/python3` text files, and Android's SELinux
 * policy refuses `execve` on *any* file under `filesDir` whatever its mode - measured, not
 * assumed: [PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet]
 * `chmod 0755`s a script this app owns and still gets rc 126. The kernel will not read the `#!`
 * line, so `tools/rootfs/shebang_exec.c` does.
 *
 * **The fourth use of the pattern [BusyBoxApplets] and [BaseSpaceInstaller] already run three
 * times.** A name resolves to a native library Android *will* exec (it lives in `nativeLibraryDir`,
 * context `apk_data_file`): `usr/local/bin/pip` -> `bin/shebang-exec` -> `libshebang.so`. `PATH` is
 * `usr/local/bin:usr/bin:bin` ([SpaceRuntime]), so the link shadows the real `usr/bin/pip` without
 * touching it, and `usr/local/bin` is empty in the image
 * (`evidence/p7e-2a-console-scripts/README.md`).
 *
 * **One maintained link, not 32.** Only `bin/shebang-exec` is repointed on an app update, because
 * `nativeLibraryDir` moves; the `usr/local/bin` links are *relative* and can never go stale, the
 * same shape [BusyBoxApplets] keeps for its 304 applets.
 *
 * ```
 * ConsoleScripts(baseDir, File(nativeLibraryDir, "libshebang.so")).relink()
 * ```
 */
class ConsoleScripts(
  private val baseDir: File,
  private val nativeTrampoline: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  /**
   * Repoints `bin/shebang-exec` and links every `#!` script in `usr/bin` from `usr/local/bin`.
   * Returns how many scripts are linked - the count of console scripts the shell can then run by
   * name. Idempotent, and run on every launch for [BusyBoxApplets.relink]'s reason: a Space
   * extracted by an older app has no links yet, and recreating a correct one costs nothing.
   */
  suspend fun relink(): Int = withContext(ioDispatcher) {
    linkTo(File(baseDir, TRAMPOLINE_PATH), nativeTrampoline.toPath())
    linkShebangScriptsFrom(File(baseDir, SCRIPT_DIR))
  }

  private fun linkShebangScriptsFrom(scriptDir: File): Int {
    val localBin = File(baseDir, LOCAL_BIN).apply { mkdirs() }
    val trampolineTarget = localBin.toPath().relativize(File(baseDir, TRAMPOLINE_PATH).toPath())
    return scriptDir.listFiles().orEmpty().count { script ->
      isShebangScript(script) && linkTo(File(localBin, script.name), trampolineTarget)
    }
  }

  /** A regular file - not an applet symlink, not an ELF - whose first two bytes are `#!`. */
  private fun isShebangScript(candidate: File): Boolean {
    if (!candidate.isFile || Files.isSymbolicLink(candidate.toPath())) return false
    return candidate.inputStream().use { it.read() == '#'.code && it.read() == '!'.code }
  }

  private fun linkTo(link: File, target: Path): Boolean {
    link.parentFile?.mkdirs()
    link.delete()
    Files.createSymbolicLink(link.toPath(), target)
    return true
  }

  private companion object {
    /** The one link repaired every launch; `libshebang.so` in `nativeLibraryDir` is its target. */
    const val TRAMPOLINE_PATH = "bin/shebang-exec"

    /** Where the console scripts live, and the only directory this class reads. */
    const val SCRIPT_DIR = "usr/bin"

    /** First on `PATH`, empty in the image - so a link here shadows without overwriting. */
    const val LOCAL_BIN = "usr/local/bin"
  }
}
