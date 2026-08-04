package com.jupydroid.app.data.space

/**
 * The per-architecture facts about the rootfs that the app has to know.
 *
 * musl names its dynamic loader after the architecture and keeps it in `/lib` on
 * every target, with no multiarch subdirectories - which is why a Space needs only
 * this one path and not the library-directory triplet glibc would require.
 *
 * [loaderLinkPath] must stay in step with `tools/rootfs/stage-app-artifacts.sh`,
 * which bakes it into the interpreter's `PT_INTERP` at build time, and with
 * `BaseSpaceInstaller`, which symlinks it into the APK native library directory -
 * the only place an ELF interpreter may live (`evidence/step8.3/musl-runs.txt`).
 */
enum class SpaceAbi(
  val androidAbi: String,
  val loaderLinkPath: String,
) {
  X86_64("x86_64", "lib/ld-musl-x86_64.so.1"),
  ARM64("arm64-v8a", "lib/ld-musl-aarch64.so.1"),
  ;

  companion object {
    fun of(androidAbi: String): SpaceAbi =
      entries.firstOrNull { it.androidAbi == androidAbi }
        ?: throw IllegalArgumentException(
          "no rootfs is built for ABI '$androidAbi'; expected one of ${entries.map { it.androidAbi }}",
        )
  }
}
