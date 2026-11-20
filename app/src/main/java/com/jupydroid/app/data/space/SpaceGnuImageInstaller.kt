package com.jupydroid.app.data.space

import com.jupydroid.app.data.disk.deleteTreeUnlinkingLinks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The gnu interpreter payload a Space gains beside its musl rootfs: the pinned CPython at
 * `opt/gnu/python`, the glibc runtime files at `usr/lib/aarch64-linux-gnu`, its loader at
 * `lib/ld-linux-aarch64.so.1`, and the kernel stack inside the CPython's own prefix
 * (ADR-0166).
 *
 * An overlay, never a re-extract: [ensureInstalled] extracts the payload into a Space that
 * already has its image and touches nothing else, so an existing Space gains the second
 * interpreter without losing what it `apk add`ed or installed (ADR-0073's rule, applied to a
 * second tree). The stamp under `.jupydroid` is what a payload bump re-extracts on; the
 * interpreter's presence is what a half extract fails.
 *
 * ```
 * SpaceGnuImageInstaller(archive = { assets.open("spaces/gnu/gnu-arm64-v8a.tzst") })
 *   .ensureInstalled(spaceDirIn(spacesDir(context), space.id))
 * ```
 */
class SpaceGnuImageInstaller(
  private val archive: ArchiveSource,
  private val bundledVersion: Int = BUNDLED_GNU_VERSION,
  private val extractor: TarZstdExtractor = TarZstdExtractor(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  /**
   * Fills [spaceDir] with the payload unless it already carries this version, and returns the
   * entries written - 0 when there was nothing to do. A version bump clears the payload's own
   * paths first, so a file only the old payload had cannot linger beside the new one.
   */
  suspend fun ensureInstalled(spaceDir: File): Int = withContext(ioDispatcher) {
    if (isInstalled(spaceDir)) return@withContext 0
    OVERLAY_PATHS.forEach { deleteTreeUnlinkingLinks(File(spaceDir, it).toPath()) }
    extractor.extract(archive, spaceDir).also { stampVersion(spaceDir) }
  }

  /** The stamp says this version and the interpreter it names is actually there. */
  private fun isInstalled(spaceDir: File): Boolean =
    installedVersion(spaceDir) == bundledVersion && File(spaceDir, GNU_INTERPRETER_PATH).isFile

  private fun installedVersion(spaceDir: File): Int? =
    File(spaceDir, STAMP_PATH).takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()

  private fun stampVersion(spaceDir: File) {
    File(spaceDir, STAMP_PATH).apply { parentFile?.mkdirs() }.writeText(bundledVersion.toString())
  }

  private companion object {
    const val STAMP_PATH = ".jupydroid/gnu-version"

    /** The payload's interpreter, which proot is handed for a glibc process. */
    const val GNU_INTERPRETER_PATH = "opt/gnu/python/bin/python3.12"

    /** Everything the payload owns inside a Space - what a version bump must clear first. */
    val OVERLAY_PATHS = listOf("opt/gnu", "usr/lib/aarch64-linux-gnu", "lib/ld-linux-aarch64.so.1")

    /** Bump when the bundled gnu payload changes so Spaces re-extract it. */
    const val BUNDLED_GNU_VERSION = 1
  }
}
