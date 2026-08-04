package com.jupydroid.app.data.space

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 *   archive = { assets.open("spaces/base/rootfs-x86_64.tgz") },
 *   baseDir = File(filesDir, "spaces/base"),
 *   nativeLoader = File(applicationInfo.nativeLibraryDir, "libld-linux.so"),
 * ).installIfNeeded()
 * ```
 */
class BaseSpaceInstaller(
  private val archive: ArchiveSource,
  private val baseDir: File,
  private val nativeLoader: File,
  private val loaderLinkPath: String = DEFAULT_LOADER_LINK_PATH,
  private val bundledVersion: Int = BUNDLED_ROOTFS_VERSION,
  private val extractor: TarGzExtractor = TarGzExtractor(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun installIfNeeded(): InstalledSpace {
    if (installedVersion() == bundledVersion) {
      return InstalledSpace(baseDir, bundledVersion, freshlyInstalled = false)
    }
    val written = extractor.extract(archive, baseDir)
    linkLoaderToNativeLibrary()
    stampVersion()
    return InstalledSpace(baseDir, bundledVersion, freshlyInstalled = true, entriesWritten = written)
  }

  /**
   * The archive ships `/lib64/<loader>` pointing inside the rootfs, but a rootfs file
   * cannot be execve'd at targetSdk 36. python3's PT_INTERP is baked with this stable
   * `/data/data/<pkg>` path, so redirect it at the native library, which can be
   * (docs/PLAN.md, "Exec model").
   */
  private suspend fun linkLoaderToNativeLibrary() = withContext(ioDispatcher) {
    require(nativeLoader.exists()) { "loader missing at '$nativeLoader'; is useLegacyPackaging on?" }
    val link = File(baseDir, loaderLinkPath)
    link.parentFile?.mkdirs()
    link.delete()
    Files.createSymbolicLink(link.toPath(), nativeLoader.toPath())
  }

  private suspend fun installedVersion(): Int? = withContext(ioDispatcher) {
    stampFile().takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
  }

  private suspend fun stampVersion() = withContext(ioDispatcher) {
    stampFile().apply { parentFile?.mkdirs() }.writeText(bundledVersion.toString())
  }

  private fun stampFile(): File = File(baseDir, STAMP_PATH)

  private companion object {
    const val DEFAULT_LOADER_LINK_PATH = "lib64/ld-linux-x86-64.so.2"
    const val STAMP_PATH = ".jupydroid/rootfs-version"

    /** Bump when the bundled rootfs changes so installed Spaces are re-extracted. */
    const val BUNDLED_ROOTFS_VERSION = 1
  }
}
