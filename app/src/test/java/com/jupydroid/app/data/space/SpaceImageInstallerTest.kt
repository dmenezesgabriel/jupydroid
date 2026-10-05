package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
