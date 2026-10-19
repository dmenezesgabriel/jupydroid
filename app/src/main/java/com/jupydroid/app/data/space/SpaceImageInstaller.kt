package com.jupydroid.app.data.space

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The bundled image turned into a runnable Space directory: an extract plus the kernelspec the
 * shipped tarball does not carry.
 *
 * The image ships `ipykernel_launcher`'s spec in `usr/share/jupyter/kernels/python3/`; stock
 * ipykernel aborts on Android, so [SpaceKernelSpec] writes our module's argv over it. No
 * `.jupydroid/rootfs-version` stamp: that stamp is base's re-extract-on-bump mechanism, and a
 * user's Space that has its rootfs is never re-extracted - its `apk add`s are its own (ADR-0073).
 * A restore brings back a Space's config and home and no rootfs (`SpaceHomeBackupScope`), so
 * [extractIfAbsent] puts the image back around them.
 *
 * ```
 * SpaceImageInstaller(archive = { assets.open("spaces/base/rootfs-arm64-v8a.tzst") })
 *   .install(targetDir) { fraction -> progressBar.value = fraction }
 * ```
 */
class SpaceImageInstaller(
  private val archive: ArchiveSource,
  private val extractor: TarZstdExtractor = TarZstdExtractor(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  /** Fills [targetDir] with the image; [onProgress] is the extract's own fraction of it. */
  suspend fun install(targetDir: File, onProgress: suspend (Float) -> Unit = {}): Int {
    val written = extractor.extract(archive, targetDir, onProgress)
    SpaceKernelSpec.install(targetDir)
    return written
  }

  /**
   * Fills [spaceDir] with the image only when it has no rootfs, and returns 0 when it has one.
   *
   * The interpreter is entry 466 of the image's 22,609, so its presence alone would read a half
   * extract as whole; a marker written before the extract and removed after it is what says one
   * was interrupted. It sits outside the home, so no backup carries it.
   *
   * ```
   * spaceImageInstaller(context).extractIfAbsent(spaceDirIn(spacesDir(context), "space-1"))
   * ```
   */
  suspend fun extractIfAbsent(spaceDir: File): Int = withContext(ioDispatcher) {
    val marker = File(spaceDir, EXTRACTING_MARKER_PATH)
    if (File(spaceDir, INTERPRETER_PATH).exists() && !marker.exists()) return@withContext 0
    marker.apply { parentFile?.mkdirs() }.writeText("")
    install(spaceDir).also { marker.delete() }
  }

  private companion object {
    /** The image's own `/usr/bin/python3`, which proot is handed for every process. */
    const val INTERPRETER_PATH = "usr/bin/python3"

    const val EXTRACTING_MARKER_PATH = ".jupydroid/image-extracting"
  }
}
