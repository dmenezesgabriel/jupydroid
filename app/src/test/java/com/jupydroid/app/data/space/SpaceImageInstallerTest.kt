package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpaceImageInstallerTest {
  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * The shipped tarball carries `ipykernel_launcher`'s spec, which cannot run here; an installed
   * image must carry ours instead - else every kernel a created Space starts would abort.
   */
  @Test
  fun install_extractsTheImageAndReplacesItsStockKernelspecWithOurs() = runTest {
    val archive = tarZst {
      file("usr/share/jupyter/kernels/python3/kernel.json", """{"argv": ["python", "-m", "ipykernel_launcher"]}""")
      file("usr/bin/python3", "elf")
    }
    val target = tempFolder.newFolder("space-1")

    val written = SpaceImageInstaller(archive = { archive.inputStream() }).install(target)

    assertEquals(2, written)
    val spec = File(target, "usr/share/jupyter/kernels/python3/kernel.json").readText()
    assertTrue("kernelspec must launch our module, was: $spec", spec.contains("jupydroid_kernel"))
    assertEquals("elf", File(target, "usr/bin/python3").readText())
  }

  /** The copy's fraction is the extract's own - the installer just hands it through. */
  @Test
  fun install_handsTheExtractsProgressThrough() = runTest {
    val archive = tarZst { file("usr/bin/a", "aaa") }
    val fractions = mutableListOf<Float>()

    SpaceImageInstaller(archive = { archive.inputStream() })
      .install(tempFolder.newFolder("space-1")) { fractions += it }

    assertTrue("expected at least one emission", fractions.isNotEmpty())
  }

  /**
   * A restore brings back a Space's config and home and never its rootfs, so the image goes in
   * around them; the home's own files and the config are left as the backup wrote them.
   */
  @Test
  fun extractIfAbsent_putsTheImageAroundARestoredHome() = runTest {
    val space = tempFolder.newFolder("space-1")
    File(space, "config.json").writeText("""{"name":"Thesis"}""")
    File(space, "home/jupydroid").mkdirs()
    File(space, "home/jupydroid/thesis.md").writeText("# Thesis")

    val written = SpaceImageInstaller(archive = { image().inputStream() }).extractIfAbsent(space)

    // The image's three files; its `home` directories are already there, so none is made.
    assertEquals(3, written)
    assertEquals("elf", File(space, "usr/bin/python3").readText())
    assertEquals("# Thesis", File(space, "home/jupydroid/thesis.md").readText())
    assertEquals("""{"name":"Thesis"}""", File(space, "config.json").readText())
    val spec = File(space, KERNEL_SPEC).readText()
    assertTrue("kernelspec must launch our module, was: $spec", spec.contains("jupydroid_kernel"))
    assertFalse("the in-progress marker must be gone", File(space, EXTRACTING_MARKER).exists())
  }

  /** A Space that has its rootfs keeps it as it is: what it installed into it is its own (ADR-0073). */
  @Test
  fun extractIfAbsent_leavesASpaceThatHasItsRootfsUntouched() = runTest {
    val space = tempFolder.newFolder("space-1")
    File(space, "usr/bin").mkdirs()
    File(space, "usr/bin/python3").writeText("the Space's own")

    val written = SpaceImageInstaller(archive = { image().inputStream() }).extractIfAbsent(space)

    assertEquals(0, written)
    assertEquals("the Space's own", File(space, "usr/bin/python3").readText())
  }

  /** The interpreter is early in the image, so only the marker tells a half extract from a whole one. */
  @Test
  fun extractIfAbsent_finishesAnExtractThatWasInterrupted() = runTest {
    val space = tempFolder.newFolder("space-1")
    File(space, "usr/bin").mkdirs()
    File(space, "usr/bin/python3").writeText("elf")
    File(space, EXTRACTING_MARKER).apply { parentFile?.mkdirs() }.writeText("")

    val written = SpaceImageInstaller(archive = { image().inputStream() }).extractIfAbsent(space)

    assertEquals(5, written)
    assertEquals("lib", File(space, "usr/lib/libz.so").readText())
    assertFalse("the in-progress marker must be gone", File(space, EXTRACTING_MARKER).exists())
  }

  private fun image(): ByteArray = tarZst {
    directory("home")
    directory("home/jupydroid")
    file("usr/bin/python3", "elf")
    file(KERNEL_SPEC, """{"argv": ["python", "-m", "ipykernel_launcher"]}""")
    file("usr/lib/libz.so", "lib")
  }

  private companion object {
    const val KERNEL_SPEC = "usr/share/jupyter/kernels/python3/kernel.json"
    const val EXTRACTING_MARKER = ".jupydroid/image-extracting"
  }
}
