package com.jupydroid.app.data.space

/**
 * The per-architecture facts about a Debian rootfs that the app has to know.
 *
 * Debian names the dynamic loader and its multiarch library directory differently
 * per architecture, and puts them in different places: `lib64/` on x86_64, `lib/`
 * on arm64. Everything else about a Space is architecture-neutral.
 *
 * These must stay in step with `tools/rootfs/stage-app-artifacts.sh`, which bakes
 * [loaderLinkPath] into the interpreter's `PT_INTERP` at build time.
 */
enum class SpaceAbi(
  val androidAbi: String,
  val triplet: String,
  val loaderLinkPath: String,
) {
  X86_64("x86_64", "x86_64-linux-gnu", "lib64/ld-linux-x86-64.so.2"),
  ARM64("arm64-v8a", "aarch64-linux-gnu", "lib/ld-linux-aarch64.so.1"),
  ;

  companion object {
    fun of(androidAbi: String): SpaceAbi =
      entries.firstOrNull { it.androidAbi == androidAbi }
        ?: throw IllegalArgumentException(
          "no rootfs is built for ABI '$androidAbi'; expected one of ${entries.map { it.androidAbi }}",
        )
  }
}
