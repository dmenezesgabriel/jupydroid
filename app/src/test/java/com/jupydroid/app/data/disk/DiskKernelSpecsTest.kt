package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.KernelSpecsContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [KernelSpecsContract] against a Space directory holding base's own `python3`, and the search
 * rules of the rootfs's `jupyter_client` (`usr/lib/python3.12/site-packages/jupyter_client/kernelspec.py`
 * `find_kernel_specs`) against a Space directory written by hand.
 */
class DiskKernelSpecsTest : KernelSpecsContract() {
  @get:Rule val tempFolder = TemporaryFolder()

  private val spaceDir: File get() = File(tempFolder.root, SPACE.id)

  override fun specs(): KernelSpecs {
    writeSpec("usr/share/jupyter/kernels/python3", "Python 3")
    return diskSpecs()
  }

  @Test
  fun specs_listsASpecFromEachDirectoryTheServerSearches() = runTest {
    writeSpec("home/jupydroid/.local/share/jupyter/kernels/in-home", "In home")
    writeSpec("pkgs/share/jupyter/kernels/in-pkgs", "In pkgs")
    writeSpec("usr/local/share/jupyter/kernels/in-local", "In local")
    writeSpec("usr/share/jupyter/kernels/python3", "Python 3")

    val names = diskSpecs().specs(SPACE).specs.map { it.name }.sorted()

    assertEquals(listOf("in-home", "in-local", "in-pkgs", "python3"), names)
  }

  /** `find_kernel_specs` keeps a name's first directory: HOME's spec hides the system's. */
  @Test
  fun specs_aNameInAnEarlierDirectoryHidesTheSameNameLater() = runTest {
    writeSpec("home/jupydroid/.local/share/jupyter/kernels/stats", "From home")
    writeSpec("usr/local/share/jupyter/kernels/stats", "From local")

    val stats = diskSpecs().specs(SPACE).named("stats")

    assertEquals("From home", stats?.displayName)
  }

  /** `_list_kernels_in` keys a directory by `f.lower()`, which is the name the server answers. */
  @Test
  fun specs_namesASpecByItsDirectoryLowercased() = runTest {
    writeSpec("usr/local/share/jupyter/kernels/Stats", "Stats")

    assertEquals("Stats", diskSpecs().specs(SPACE).named("stats")?.displayName)
  }

  @Test
  fun specs_skipsADirectoryNameTheKernelNamePatternRefuses() = runTest {
    writeSpec("usr/local/share/jupyter/kernels/has space", "Refused")

    assertEquals(listOf("python3"), diskSpecs().specs(SPACE).specs.map { it.name })
  }

  @Test
  fun specs_skipsADirectoryWithNoKernelJson() = runTest {
    File(spaceDir, "usr/local/share/jupyter/kernels/empty").mkdirs()

    assertEquals(listOf("python3"), diskSpecs().specs(SPACE).specs.map { it.name })
  }

  /** `get_all_specs` drops a spec that fails to load rather than failing the whole list. */
  @Test
  fun specs_skipsAKernelJsonThatDoesNotParse() = runTest {
    kernelJson("usr/local/share/jupyter/kernels/broken").writeText("{not json")
    kernelJson("usr/local/share/jupyter/kernels/unnamed").writeText("""{"language": "python"}""")

    assertEquals(listOf("python3"), diskSpecs().specs(SPACE).specs.map { it.name })
  }

  /**
   * `ensure_native_kernel`: with no `python3` on disk - a base not yet extracted - the server still
   * lists ipykernel's own, so a new notebook's default has a spec to record.
   */
  @Test
  fun specs_onAnEmptySpaceListsTheNativePython3AsDefault() = runTest {
    val models = diskSpecs().specs(SPACE)

    assertEquals(listOf(PYTHON_SPEC), models.specs)
    assertEquals(PYTHON_SPEC, models.defaultSpec)
  }

  /** The Space's chosen default is what the server will be started with, so a new notebook records it. */
  @Test
  fun specs_aStoredDefaultWithItsSpecOnDiskIsTheDefault() = runTest {
    writeSpec("usr/local/share/jupyter/kernels/stats", "Python (stats)")

    assertEquals("stats", diskSpecs(storedDefault = "stats").specs(SPACE).default)
  }

  /** A spec deleted after Save: `defaultSpec` would refuse a default the catalogue does not list. */
  @Test
  fun specs_aStoredDefaultWithNoSpecOnDiskFallsBackToPython3() = runTest {
    assertEquals("python3", diskSpecs(storedDefault = "stats").specs(SPACE).default)
  }

  @Test
  fun specs_readsTheLanguageFromTheKernelJson() = runTest {
    writeSpec("usr/local/share/jupyter/kernels/ir", "R", language = "R")

    assertEquals(KernelSpec("ir", "R", "R"), diskSpecs().specs(SPACE).named("ir"))
  }

  private fun diskSpecs(storedDefault: String = "python3") = DiskKernelSpecs(tempFolder.root, storedDefaultKernelOf = {
    storedDefault
  })

  private fun writeSpec(dir: String, displayName: String, language: String = "python") {
    kernelJson(dir).writeText("""{"argv": [], "display_name": "$displayName", "language": "$language"}""")
  }

  private fun kernelJson(dir: String): File = File(spaceDir, "$dir/kernel.json").also { it.parentFile?.mkdirs() }
}
