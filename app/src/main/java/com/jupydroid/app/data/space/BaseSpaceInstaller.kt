package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.BUNDLED_PYTHON_VERSION
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/** Outcome of [BaseSpaceInstaller.installIfNeeded]. */
data class InstalledSpace(
  val baseDir: File,
  val version: Int,
  val freshlyInstalled: Boolean,
  val entriesWritten: Int = 0,
)

/**
 * Unpacks the bundled base Space on first run, then gets out of the way.
 *
 * Idempotent through a stamp file holding the bundled version, the same trick as
 * Winlator's `.rfs_version`: re-running is free, and bumping [bundledVersion] in a
 * release re-extracts over the old tree.
 *
 * ```
 * BaseSpaceInstaller(
 *   archive = { assets.open("spaces/base/rootfs-arm64-v8a.tzst") },
 *   baseDir = File(filesDir, "spaces/base"),
 *   nativeLoader = File(applicationInfo.nativeLibraryDir, "libloader.so"),
 *   nativeInterpreter = File(applicationInfo.nativeLibraryDir, "libpython3.so"),
 *   nativeBusyBox = File(applicationInfo.nativeLibraryDir, "libbusybox.so"),
 * ).installIfNeeded()
 * ```
 */
class BaseSpaceInstaller(
  private val archive: ArchiveSource,
  private val baseDir: File,
  private val nativeLoader: File,
  private val nativeInterpreter: File,
  private val nativeBusyBox: File,
  private val nativeTrampoline: File,
  private val loaderLinkPath: String = SpaceAbi.ARM64.loaderLinkPath,
  private val bundledVersion: Int = BUNDLED_ROOTFS_VERSION,
  private val extractor: TarZstdExtractor = TarZstdExtractor(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun installIfNeeded(): InstalledSpace {
    val alreadyInstalled = installedVersion() == bundledVersion
    val written = if (alreadyInstalled) 0 else extractor.extract(archive, baseDir)

    // Always, not just on a fresh extract: nativeLibraryDir changes on every app
    // update while the extracted Space survives, so a stale link would leave exec
    // failing with ENOENT until the user cleared the app's data.
    linkLoaderToNativeLibrary()
    linkInterpreterToNativeLibrary()
    BusyBoxApplets(baseDir, nativeBusyBox, ioDispatcher).relink()
    ConsoleScripts(baseDir, nativeTrampoline, ioDispatcher).relink()
    SpaceKernelSpec.install(baseDir, nativeInterpreter, ioDispatcher)

    if (!alreadyInstalled) stampVersion()
    return InstalledSpace(baseDir, bundledVersion, !alreadyInstalled, written)
  }

  /**
   * The archive ships `/lib64/<loader>` pointing inside the rootfs, but a rootfs file
   * cannot be execve'd at targetSdk 36. python3's PT_INTERP is baked with this stable
   * `/data/data/<pkg>` path, so redirect it at the native library, which can be
   * (docs/DECISIONS.md, "Exec model").
   */
  private suspend fun linkLoaderToNativeLibrary() {
    require(nativeLoader.exists()) { "loader missing at '$nativeLoader'; is useLegacyPackaging on?" }
    linkToNativeLibrary(loaderLinkPath, nativeLoader)
  }

  /**
   * Gives the Space's *shell* a `python3` it can actually run (P7e-1).
   *
   * The kernel never needed this - it is exec'd from [nativeInterpreter] directly
   * ([SpaceRuntime]) - but a terminal resolves the name through `PATH` and lands on the rootfs
   * copy, which is refused twice over: it is an ELF under `filesDir`, and [TarZstdExtractor]
   * deliberately writes no executable bit. Both refusals are the *file's*, so a symlink settles
   * them: `execve` walks the link and runs its checks on the target, exactly as `bin/busybox`
   * relies on ([BusyBoxApplets]).
   *
   * **[INTERPRETER_PATH] rather than `bin/python3`, and `PATH` order is why.** [SpaceRuntime]
   * searches `usr/local/bin:usr/bin:bin`, so a new name in `bin/` is shadowed by the
   * `usr/bin/python3` the image already ships and is never reached. Replacing the one real ELF
   * instead leaves that link - and `usr/bin/python` above it - pointing *relatively* at a name
   * that now resolves, so all three work and only this one can ever go stale.
   */
  private suspend fun linkInterpreterToNativeLibrary() {
    require(nativeInterpreter.exists()) {
      "interpreter missing at '$nativeInterpreter'; is useLegacyPackaging on?"
    }
    linkToNativeLibrary(INTERPRETER_PATH, nativeInterpreter)
  }

  /** Replaces whatever the archive left at [relativePath] with a link into `nativeLibraryDir`. */
  private suspend fun linkToNativeLibrary(relativePath: String, nativeLibrary: File) = withContext(ioDispatcher) {
    val link = File(baseDir, relativePath)
    link.parentFile?.mkdirs()
    link.delete()
    Files.createSymbolicLink(link.toPath(), nativeLibrary.toPath())
  }

  private suspend fun installedVersion(): Int? = withContext(ioDispatcher) {
    stampFile().takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
  }

  private suspend fun stampVersion() = withContext(ioDispatcher) {
    stampFile().apply { parentFile?.mkdirs() }.writeText(bundledVersion.toString())
  }

  private fun stampFile(): File = File(baseDir, STAMP_PATH)

  private companion object {
    const val STAMP_PATH = ".jupydroid/rootfs-version"

    /**
     * The image's one real interpreter ELF, which `usr/bin/python3` and `usr/bin/python` both
     * point at. **It must name the same file `tools/rootfs/stage-app-artifacts.sh` stages as
     * `libpython3.so`** - the link is only right because the two are the same bytes.
     *
     * The version comes from [BUNDLED_PYTHON_VERSION] rather than being spelled again: it is the
     * same fact a Space's own `pythonVersion` records, and one of the two would go stale.
     */
    const val INTERPRETER_PATH = "usr/bin/python$BUNDLED_PYTHON_VERSION"

    /** Bump when the bundled rootfs changes so installed Spaces are re-extracted. */
    const val BUNDLED_ROOTFS_VERSION = 9
  }
}
