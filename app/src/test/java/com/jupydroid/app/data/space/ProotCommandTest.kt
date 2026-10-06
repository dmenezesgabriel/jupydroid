package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** proot's command line and the environment it reads, built once for every process a Space runs. */
class ProotCommandTest {
  private val nativeDir = File("/data/app/~~abc==/com.jupydroid.app-x==/lib/arm64")
  private val root = File("/data/data/com.jupydroid.app/files/spaces/space-1")
  private val proot = ProotCommand(nativeLibraryDir = nativeDir, root = root)

  @Test
  fun theCommandRunsUnderProotFromTheNativeLibraryDir() {
    val argv = proot.argv(listOf("/usr/bin/python3", "-c", "pass"))

    assertEquals(File(nativeDir, "libproot.so").path, argv.first())
    assertEquals(listOf("/usr/bin/python3", "-c", "pass"), argv.takeLast(3))
  }

  /** `-0` is what `apk add` needs; `/dev` and `/proc` are the device's, not the image's. */
  @Test
  fun theRootIsTheSpaceAndUidZeroWithDevAndProcBoundThrough() {
    assertEquals(
      listOf("-r", root.path, "-0", "-w", "/", "-b", "/dev", "-b", "/proc"),
      proot.argv(emptyList()).drop(1),
    )
  }

  /** The working directory is named inside the root, because that is the only filesystem the process sees. */
  @Test
  fun theProcessStartsInTheWorkingDirectoryItIsGivenInsideTheRoot() {
    val argv = ProotCommand(nativeDir, root, inRootWorkingDir = "/home/jupydroid").argv(emptyList())

    assertEquals("/home/jupydroid", argv[argv.indexOf("-w") + 1])
  }

  /**
   * Off unless asked for: a server's kernels must outlive nothing but the server, and they do, but a
   * bounded command must take every process it started with it when it exits.
   */
  @Test
  fun killOnExitIsAnOptionBeforeTheCommandAndOnlyWhenAsked() {
    val bounded = proot.argv(listOf("/bin/sh"), killOnExit = true)

    assertEquals(listOf(File(nativeDir, "libproot.so").path, "--kill-on-exit"), bounded.take(2))
    assertEquals(false, "--kill-on-exit" in proot.argv(listOf("/bin/sh")))
  }

  @Test
  fun theLoaderIsTheOneShippedBesideProot() {
    assertEquals(File(nativeDir, "libproot-loader.so").path, proot.environment().getValue("PROOT_LOADER"))
  }

  @Test
  fun prootKeepsItsScratchInsideTheSpace() {
    assertEquals(File(root, "tmp").path, proot.environment().getValue("PROOT_TMP_DIR"))
  }
}
