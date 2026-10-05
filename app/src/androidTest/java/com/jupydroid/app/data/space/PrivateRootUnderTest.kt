package com.jupydroid.app.data.space

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.TimeSource

/**
 * **A private copy of base's image, and proot to run it as `/`**, for the device probes that
 * measure what a Space owning its whole system can do (ADR-0073).
 *
 * proot and its loader ship only in a build made with `-Pjupydroid.isolationProbe` after
 * `scripts/stage_isolation_probe.py`; [hasProot] is what a probe `Assume`s on.
 *
 * ```
 * val root = PrivateRootUnderTest(context, "isolation-private-root")
 * root.extract(); root.prepareForProot()
 * ProcessBuilder(root.prootCommand(listOf("/bin/sh", "-c", "true")))
 * ```
 */
class PrivateRootUnderTest(private val context: Context, name: String) {
  val dir: File = File(spacesDir(context), name)
  val nativeDir: String = context.applicationInfo.nativeLibraryDir

  val hasProot: Boolean get() = File(nativeDir, PROOT).exists()

  /** Fresh for every caller that asks, so nothing reads what an earlier run installed. */
  fun extract() {
    dir.deleteRecursively()
    val start = TimeSource.Monotonic.markNow()
    val entries = runBlocking {
      TarZstdExtractor().extract({ context.assets.open("spaces/base/rootfs-arm64-v8a.tzst") }, dir)
    }
    Log.i(PROBE_TAG, "private rootfs extracted entries=$entries ms=${start.elapsedNow().inWholeMilliseconds}")
  }

  /** The extractor writes no executable bit, and proot checks it where the kernel would. */
  fun prepareForProot() {
    runInSpace(spaceShellIn(baseSpaceDir(context)).path, "-c", "chmod -R u+x ${dir.path}")
    File(dir, "etc/resolv.conf").writeText("nameserver $PUBLIC_DNS\n")
  }

  /**
   * The environment proot itself reads. `PROOT_LOADER` names the loader carved out beside it,
   * because unset proot writes its embedded one under `PROOT_TMP_DIR` - `filesDir` - and execs it
   * there, which targetSdk 36 refuses.
   */
  fun prootEnvironment(): Map<String, String> = mapOf(
    "PROOT_LOADER" to File(nativeDir, PROOT_LOADER).path,
    "PROOT_TMP_DIR" to File(dir, "tmp").path,
  )

  /** [command] run with [dir] as `/` and uid 0, `/dev` and `/proc` bound through. */
  fun prootCommand(command: List<String>): List<String> =
    listOf(File(nativeDir, PROOT).path, "-r", dir.path, "-0", "-w", "/", "-b", "/dev", "-b", "/proc") + command

  /**
   * The same launch as a shell string, to be run from inside [nativeDir]: its path holds `==`,
   * which `env` reads as an assignment. `env -i`, because base's preloads are absolute paths proot
   * would translate into the private root, where they are not.
   */
  fun prootShellPrefix(timeoutSeconds: Int): String {
    val environment = prootEnvironment() + mapOf("PATH" to IN_ROOT_PATH, "HOME" to "/root")
    val assignments = environment.entries.joinToString(" ") { "${it.key}=${it.value}" }
    val args = prootCommand(emptyList()).drop(1).joinToString(" ")
    return "timeout $timeoutSeconds env -i $assignments ./$PROOT $args"
  }

  /** 407 MB a copy: not left behind for the next test, or on the device. */
  fun delete() {
    dir.deleteRecursively()
  }

  companion object {
    const val PROBE_TAG = "JupydroidIsolation"
    const val IN_ROOT_PATH = "/usr/sbin:/usr/bin:/sbin:/bin"
    private const val PROOT = "libproot.so"
    private const val PROOT_LOADER = "libproot-loader.so"
    private const val PUBLIC_DNS = "1.1.1.1"
  }
}
