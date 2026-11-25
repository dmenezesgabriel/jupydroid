package com.jupydroid.app.data.disk

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
import java.nio.file.Files
import java.nio.file.Paths

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
    environments.filterNot { it.isMissing }.forEach { makeVenv(it.path, withIpykernel = it.hasIpykernel) }
    environments.filter { it.isMissing }.forEach { writeSpecOfGoneVenv(it) }
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

  /**
   * A project's own env folders are searched one level down, as vscode-python's per-folder
   * locator searches each opened folder - and no `.ipynb` or `pyproject.toml` is required: the
   * env's own `pyvenv.cfg` is the signal a folder holds one.
   */
  @Test
  fun environments_findsAnEnvironmentInsideAProjectFolder() = runTest {
    makeVenv("thesis/.venv")
    makeVenv("proj-b/envs/stats", withIpykernel = true)

    assertEquals(
      listOf(
        PythonEnvironment("proj-b/envs/stats", hasIpykernel = true),
        PythonEnvironment("thesis/.venv", hasIpykernel = false),
      ),
      listed(),
    )
  }

  /** The bound is one project level: env roots inside a project, never deeper. */
  @Test
  fun environments_doesNotSearchBelowOneProjectLevel() = runTest {
    makeVenv("thesis/sub/.venv")
    makeVenv("envs/group/deep")

    assertEquals(emptyList<PythonEnvironment>(), listed())
  }

  /** Hidden folders are tool state, not projects: `.git` and `.ipynb_checkpoints` hold no envs. */
  @Test
  fun environments_skipsHiddenFoldersAsProjects() = runTest {
    makeVenv(".hidden/.venv")
    makeVenv(".git/envs/x")

    assertEquals(emptyList<PythonEnvironment>(), listed())
  }

  /** A container's children are environments already; scanning it as a project would list them twice. */
  @Test
  fun environments_doesNotListAnEnvironmentTwice() = runTest {
    makeVenv("envs/stats", withIpykernel = true)

    assertEquals(listOf(PythonEnvironment("envs/stats", hasIpykernel = true)), listed())
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

    val specs = DiskKernelSpecs(tempFolder.root).specs(Space(EMPTY_SPACE_ID, "Thesis", emptyList())).specs

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

  /** `rm -r ~/envs/stats` after Register: the spec stays, naming an interpreter that is gone. */
  @Test
  fun environments_listsARegisteredEnvironmentDeletedSinceAsGone() = runTest {
    val port = environmentsHolding(STATS)
    port.register(EMPTY_SPACE_ID, STATS.path)
    File(workspace, STATS.path).deleteRecursively()

    assertEquals(
      listOf(PythonEnvironment("envs/stats", hasIpykernel = false, registeredAs = "stats", isMissing = true)),
      port.environments(EMPTY_SPACE_ID),
    )
  }

  /** A spec outside the workspace that names a missing program is no environment of the Space's. */
  @Test
  fun environments_aGoneInterpreterOutsideTheWorkspaceIsNoEnvironment() = runTest {
    val port = environmentsHolding()
    val other = File(spaceDir, "usr/local/share/jupyter/kernels/old").apply { mkdirs() }
    File(other, "kernel.json").writeText("""{"argv": ["/opt/old/bin/python"], "display_name": "Old"}""")

    assertEquals(emptyList<PythonEnvironment>(), port.environments(EMPTY_SPACE_ID))
  }

  /** Making the environment again where it was brings its kernel back: the spec is the record. */
  @Test
  fun environments_aGoneEnvironmentMadeAgainIsRegisteredOnceMore() = runTest {
    val port = environmentsHolding(STATS)
    port.register(EMPTY_SPACE_ID, STATS.path)
    File(workspace, STATS.path).deleteRecursively()
    makeVenv(STATS.path, withIpykernel = true)

    assertEquals(listOf(STATS.copy(registeredAs = "stats")), port.environments(EMPTY_SPACE_ID))
  }

  /** A project env registers under its whole path, so `proj-a` and `proj-b` never share a spec name. */
  @Test
  fun register_registersAnEnvironmentInsideAProjectFolder() = runTest {
    makeVenv("proj-a/.venv", withIpykernel = true)

    val name = environments().register(EMPTY_SPACE_ID, "proj-a/.venv")

    assertEquals("proj-a-.venv", name)
    assertEquals("/home/jupydroid/proj-a/.venv/bin/python", registeredArgv("proj-a-.venv").first())
    val specs = DiskKernelSpecs(tempFolder.root).specs(Space(EMPTY_SPACE_ID, "Thesis", emptyList())).specs
    assertTrue(KernelSpec("proj-a-.venv", "Python (proj-a/.venv)", "python") in specs)
  }

  /** `rm -r proj-a/.venv` after Register: the spec stays, naming an interpreter that is gone. */
  @Test
  fun environments_listsARegisteredProjectEnvironmentDeletedSinceAsGone() = runTest {
    makeVenv("proj-a/.venv", withIpykernel = true)
    val port = environments()
    port.register(EMPTY_SPACE_ID, "proj-a/.venv")
    File(workspace, "proj-a/.venv").deleteRecursively()

    assertEquals(
      listOf(
        PythonEnvironment(
          "proj-a/.venv",
          hasIpykernel = false,
          registeredAs = "proj-a-.venv",
          isMissing = true,
        ),
      ),
      port.environments(EMPTY_SPACE_ID),
    )
  }

  private fun registeredArgv(name: String): List<String> =
    Json.parseToJsonElement(File(spaceDir, "usr/local/share/jupyter/kernels/$name/kernel.json").readText())
      .jsonObject.getValue("argv").jsonArray.map { it.jsonPrimitive.content }

  private suspend fun listed(): List<PythonEnvironment> = environments().environments(EMPTY_SPACE_ID)

  /** The spec Register wrote for [gone], whose directory has since been deleted. */
  private fun writeSpecOfGoneVenv(gone: PythonEnvironment) {
    val specDir = File(spaceDir, "usr/local/share/jupyter/kernels/${gone.registeredAs}").apply { mkdirs() }
    File(specDir, "kernel.json").writeText("""{"argv": ["/home/jupydroid/${gone.path}/bin/python"]}""")
  }

  /**
   * What `python3 -m venv` and `uv venv` both leave: `pyvenv.cfg` at the root, `bin/python` linking to
   * the Space's own interpreter - a target the host cannot resolve - and packages under `lib`.
   */
  private fun makeVenv(path: String, withIpykernel: Boolean = false) {
    val root = File(workspace, path).apply { mkdirs() }
    File(root, "pyvenv.cfg").writeText("home = /usr/bin\nversion = 3.12.10\n")
    val bin = File(root, "bin").apply { mkdirs() }
    Files.createSymbolicLink(File(bin, "python").toPath(), Paths.get("/usr/bin/python3"))
    val sitePackages = File(root, "lib/python3.12/site-packages").apply { mkdirs() }
    if (!withIpykernel) return
    File(sitePackages, "ipykernel").mkdirs()
    File(sitePackages, "ipykernel/__init__.py").writeText("")
  }
}
