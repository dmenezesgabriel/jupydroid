package com.jupydroid.app.data.space

import java.io.File

/**
 * The bundled image turned into a runnable Space directory: an extract plus the kernelspec the
 * shipped tarball does not carry.
 *
 * The image ships `ipykernel_launcher`'s spec in `usr/share/jupyter/kernels/python3/`; stock
 * ipykernel aborts on Android, so [SpaceKernelSpec] writes our module's argv over it. No
 * `.jupydroid/rootfs-version` stamp: that stamp is base's re-extract-on-bump mechanism, and a
 * user's Space is never re-extracted - its `apk add`s are its own (ADR-0073).
 *
 * ```
 * SpaceImageInstaller(archive = { assets.open("spaces/base/rootfs-arm64-v8a.tzst") })
 *   .install(targetDir) { fraction -> progressBar.value = fraction }
 * ```
 */
class SpaceImageInstaller(
  private val archive: ArchiveSource,
  private val extractor: TarZstdExtractor = TarZstdExtractor(),
) {
  /** Fills [targetDir] with the image; [onProgress] is the extract's own fraction of it. */
  suspend fun install(targetDir: File, onProgress: suspend (Float) -> Unit = {}): Int {
    val written = extractor.extract(archive, targetDir, onProgress)
    SpaceKernelSpec.install(targetDir)
    return written
  }
}
