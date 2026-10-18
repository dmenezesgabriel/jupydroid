package com.jupydroid.app.data.space

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
 * Under proot the image runs as built (ADR-0073), so an install is an extract, a sweep of
 * the links an older install made into `nativeLibraryDir`, and the kernelspec. Nothing a
 * Space runs may point outside it: proot translates every path it is handed into the
 * root, where a `nativeLibraryDir` target does not exist.
 *
 * ```
 * BaseSpaceInstaller(
 *   archive = { assets.open("spaces/base/rootfs-arm64-v8a.tzst") },
 *   baseDir = File(filesDir, "spaces/base"),
 * ).installIfNeeded()
 * ```
 */
class BaseSpaceInstaller(
  private val archive: ArchiveSource,
  private val baseDir: File,
  private val bundledVersion: Int = BUNDLED_ROOTFS_VERSION,
  private val extractor: TarZstdExtractor = TarZstdExtractor(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun installIfNeeded(): InstalledSpace {
    val alreadyInstalled = isInstalled()
    val written = if (alreadyInstalled) 0 else extractor.extract(archive, baseDir)
    sweepStaleScriptLinks()
    SpaceKernelSpec.install(baseDir, ioDispatcher)
    withContext(ioDispatcher) {
      SpacePipConfig.ensure(baseDir)
      SpaceOpencodeConfig.ensure(baseDir)
    }
    if (!alreadyInstalled) stampVersion()
    return InstalledSpace(baseDir, bundledVersion, !alreadyInstalled, written)
  }

  /**
   * Whether the bundled image is in place. The stamp is written last, so a half extract reads false -
   * and the directory alone would not do: it exists before the image, holding the notebook
   * migration's own stamp.
   */
  suspend fun isInstalled(): Boolean = installedVersion() == bundledVersion

  /**
   * The console-script links an install from before proot left: `usr/local/bin` held one link
   * per script onto `bin/shebang-exec`, and neither name exists in the archive, so no re-extract
   * replaces them. [SHEBANG_EXEC_PATH] goes first, which is what makes the script links
   * dangling. Run on every launch, not only a re-extract: it is idempotent, and the walk is a
   * single directory.
   *
   * A user's own entry survives: a regular file is not a link, and a link that still resolves
   * inside the Space is not stale.
   */
  private suspend fun sweepStaleScriptLinks() = withContext(ioDispatcher) {
    File(baseDir, SHEBANG_EXEC_PATH).delete()
    File(baseDir, CONSOLE_SCRIPT_DIR).listFiles().orEmpty().forEach { entry ->
      if (entry.isUnresolvedOrExternalLink()) entry.delete()
    }
  }

  /** A symlink whose target is gone or points out of the Space - the two shapes the surgery left. */
  private fun File.isUnresolvedOrExternalLink(): Boolean {
    if (!Files.isSymbolicLink(toPath())) return false
    val target = canonicalFile
    return !target.exists() || !target.path.startsWith(baseDir.canonicalPath + File.separator)
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

    /** `usr/local/bin/<script>` -> `bin/shebang-exec`: the trampoline only the surgery made. */
    const val SHEBANG_EXEC_PATH = "bin/shebang-exec"

    /** Empty in the image, so every link here is one the surgery or the user made. */
    const val CONSOLE_SCRIPT_DIR = "usr/local/bin"

    /** Bump when the bundled rootfs changes so installed Spaces are re-extracted. */
    const val BUNDLED_ROOTFS_VERSION = 14
  }
}
