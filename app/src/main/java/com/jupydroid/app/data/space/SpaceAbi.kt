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
 * the only place an ELF interpreter may live.
 *
 * **arm64 only, and deliberately.** Android's seccomp policy is per-architecture and
 * its x86_64 filter is the stricter one: musl's libc startup is killed there under
 * conditions arm64 survives, so no Space can run on an x86_64 device however it is
 * built. Listing x86_64 here would only
 * promise a runtime that cannot exist. The app still installs on x86_64 emulators for
 * UI work; [of] is what tells it there is no Space to run.
 */
enum class SpaceAbi(val androidAbi: String, val loaderLinkPath: String) {
  ARM64("arm64-v8a", "lib/ld-musl-aarch64.so.1"),
  ;

  companion object {
    fun of(androidAbi: String): SpaceAbi = entries.firstOrNull { it.androidAbi == androidAbi }
      ?: throw IllegalArgumentException(
        "no rootfs is built for ABI '$androidAbi'; expected one of ${entries.map { it.androidAbi }}",
      )
  }
}
