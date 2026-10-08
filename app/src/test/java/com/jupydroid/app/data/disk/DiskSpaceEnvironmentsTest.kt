package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceEnvironmentsContract
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [SpaceEnvironmentsContract] against a spaces directory on disk, and the roots vscode-python
 * searches (`microsoft/vscode-python@f4c86e4f5f7c34b99c5333b1aef50e997288f936`,
 * `workspaceVirtualEnvLocator.ts` and `globalVirtualEnvronmentLocator.ts`) against a workspace
 * written by hand.
 */
class DiskSpaceEnvironmentsTest : SpaceEnvironmentsContract() {
  @get:Rule val tempFolder = TemporaryFolder()

  private val workspace: File get() = File(tempFolder.root, "$EMPTY_SPACE_ID/home/jupydroid")

  private val spaceDir: File get() = File(tempFolder.root, EMPTY_SPACE_ID)

  override fun environmentsHolding(vararg environments: PythonEnvironment): SpaceEnvironments {
    workspace.mkdirs()
    environments.forEach { makeVenv(it.path, withIpykernel = it.hasIpykernel) }
    return DiskSpaceEnvironments(tempFolder.root)
  }

  @Test
  fun environments_listsAVenvDirectlyInTheWorkspace() = runTest {
    makeVenv(".venv")

    assertEquals(listOf(PythonEnvironment(".venv", hasIpykernel = false)), listed())
  }

  @Test
  fun environments_listsAVenvInEachConventionalEnvsDirectory() = runTest {
    listOf("envs/a", "Envs/b", ".direnv/c", ".venvs/d", ".virtualenvs/e", ".local/share/virtualenvs/f")
      .forEach(::makeVenv)

    val paths = listed().map { it.path }

    assertEquals(
      listOf(".direnv/c", ".local/share/virtualenvs/f", ".venvs/d", ".virtualenvs/e", "Envs/b", "envs/a"),
      paths,
    )
  }

  @Test
  fun environments_saysWhichHaveIpykernelInTheirSitePackages() = runTest {
    makeVenv("envs/stats", withIpykernel = true)
    makeVenv("envs/bare")

    val listed = listed()

    assertEquals(
      listOf(
        PythonEnvironment("envs/bare", hasIpykernel = false),
        PythonEnvironment("envs/stats", hasIpykernel = true),
      ),
      listed,
    )
  }

  /** The roots are fixed, as vscode-python's are: a project's own `.venv` is one level too deep. */
  @Test
  fun environments_doesNotSearchBelowTheRoots() = runTest {
    makeVenv("thesis/.venv")
    makeVenv("envs/group/deep")

    assertEquals(emptyList<PythonEnvironment>(), listed())
  }

  @Test
  fun environments_skipsADirectoryWithNoPyvenvCfg() = runTest {
    File(workspace, "envs/notes/bin").mkdirs()
    File(workspace, "plain").mkdirs()

    assertEquals(emptyList<PythonEnvironment>(), listed())
  }

  /** `<sys.prefix>/local/share/jupyter`: on the server's search path, and never in the workspace. */
  @Test
  fun register_writesTheSpecIntoTheSpacesLocalKernelsDirectory() = runTest {
    environmentsHolding(STATS).register(EMPTY_SPACE_ID, STATS.path)

    assertTrue(File(spaceDir, "usr/local/share/jupyter/kernels/stats/kernel.json").isFile)
  }

  /** The env's own interpreter, named as a process in the Space sees it, so `sys.prefix` is the env. */
  @Test
  fun register_startsTheKernelWithTheEnvironmentsOwnInterpreter() = runTest {
    environmentsHolding(STATS).register(EMPTY_SPACE_ID, STATS.path)

    assertEquals("/home/jupydroid/envs/stats/bin/python", registeredArgv("stats").first())
  }

  /** What the Space's server will list, read by the same rules the stopped picker reads. */
  @Test
  fun register_isListedByTheStoppedSpacesKernelPicker() = runTest {
    environmentsHolding(STATS).register(EMPTY_SPACE_ID, STATS.path)

    val specs = DiskKernelSpecs(SpaceDirs(spaceDir)).specs(Space(EMPTY_SPACE_ID, "Thesis", emptyList())).specs

    assertTrue(specs.toString(), KernelSpec("stats", "Python (stats)", "python") in specs)
  }

  /** A spec the person put on any searched path already owns the name: the server shows only one. */
  @Test
  fun register_refusesANameAKernelspecOnTheSearchPathAlreadyHas() = runTest {
    val port = environmentsHolding(STATS)
    val existing = File(workspace, ".local/share/jupyter/kernels/stats").apply { mkdirs() }
    File(existing, "kernel.json").writeText("""{"argv": ["python"], "display_name": "Mine"}""")

    val refusal = runCatching { port.register(EMPTY_SPACE_ID, STATS.path) }.exceptionOrNull()

    assertTrue("must name the holder, was '${refusal?.message}'", refusal?.message?.contains("$existing") == true)
  }

  /** Registered means a spec runs *this* env's interpreter, not that some spec shares its name. */
  @Test
  fun environments_aSpecRunningAnotherInterpreterDoesNotRegisterTheEnvironment() = runTest {
    val port = environmentsHolding(STATS)
    val other = File(spaceDir, "usr/local/share/jupyter/kernels/stats").apply { mkdirs() }
    File(other, "kernel.json").writeText("""{"argv": ["/usr/bin/python3"], "display_name": "Other"}""")

    assertEquals(listOf(STATS), port.environments(EMPTY_SPACE_ID))
  }

  private fun registeredArgv(name: String): List<String> =
    Json.parseToJsonElement(File(spaceDir, "usr/local/share/jupyter/kernels/$name/kernel.json").readText())
      .jsonObject.getValue("argv").jsonArray.map { it.jsonPrimitive.content }

  private suspend fun listed(): List<PythonEnvironment> = environments().environments(EMPTY_SPACE_ID)

  /** What `python3 -m venv` and `uv venv` both leave: `pyvenv.cfg` at the root, packages under `lib`. */
  private fun makeVenv(path: String, withIpykernel: Boolean = false) {
    val root = File(workspace, path).apply { mkdirs() }
    File(root, "pyvenv.cfg").writeText("home = /usr/bin\nversion = 3.12.10\n")
    val sitePackages = File(root, "lib/python3.12/site-packages").apply { mkdirs() }
    if (!withIpykernel) return
    File(sitePackages, "ipykernel").mkdirs()
    File(sitePackages, "ipykernel/__init__.py").writeText("")
  }
}
