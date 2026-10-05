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
 * proot and its loader ship in arm64 `jniLibs`, and [proot] is the shipped command line.
 *
 * ```
 * val root = PrivateRootUnderTest(context, "proot-runtime-probe")
 * root.extract(); root.prepareForProot()
 * ProcessBuilder(root.proot.argv(listOf("/bin/sh", "-c", "true")))
 * ```
 */
class PrivateRootUnderTest(private val context: Context, name: String) {
  val dir: File = File(spacesDir(context), name)
  val nativeDir: String = context.applicationInfo.nativeLibraryDir

  /** How every process in [dir] is launched: the shipped command line, not a copy of it. */
  val proot = ProotCommand(File(nativeDir), dir)

  /** Fresh for every caller that asks, so nothing reads what an earlier run installed. */
  fun extract() {
    dir.deleteRecursively()
    val start = TimeSource.Monotonic.markNow()
    val entries = runBlocking {
      TarZstdExtractor().extract({ context.assets.open("spaces/base/rootfs-arm64-v8a.tzst") }, dir)
    }
    Log.i(PROBE_TAG, "private rootfs extracted entries=$entries ms=${start.elapsedNow().inWholeMilliseconds}")
  }

  /**
   * The image's own `etc/resolv.conf` ships empty, so a private root gets a public nameserver for
   * the DNS a probe asks for - the same file a Space's own resolver reads through the translated
   * root, which is exactly why proot makes it reachable.
   */
  fun prepareForProot() {
    File(dir, "etc/resolv.conf").writeText("nameserver $PUBLIC_DNS\n")
  }

  /** 407 MB a copy: not left behind for the next test, or on the device. */
  fun delete() {
    dir.deleteRecursively()
  }

  companion object {
    const val PROBE_TAG = "JupydroidIsolation"
    const val IN_ROOT_PATH = "/usr/sbin:/usr/bin:/sbin:/bin"
    private const val PUBLIC_DNS = "1.1.1.1"
  }
}
