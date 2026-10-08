package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceEnvironmentsContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

  override fun environments(): SpaceEnvironments {
    workspace.mkdirs()
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
