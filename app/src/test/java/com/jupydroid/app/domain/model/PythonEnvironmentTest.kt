package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonEnvironmentTest {
  /** `~` is the workspace in a Space's terminal, so the command pastes there as it reads. */
  @Test
  fun ipykernelInstallCommand_runsTheEnvironmentsOwnPipFromTheWorkspace() {
    val stats = PythonEnvironment(path = "envs/stats", hasIpykernel = false)

    assertEquals("~/envs/stats/bin/pip install ipykernel", stats.ipykernelInstallCommand)
  }

  /** jupyter_client lists a spec under its lowercased directory name, so the name is born lowercase. */
  @Test
  fun kernelName_isTheEnvironmentsDirectoryLowercased() {
    assertEquals("stats", PythonEnvironment("Envs/Stats", hasIpykernel = true).kernelName)
  }

  /** `envs/` is a container, not part of the env's name: a spec already on disk keeps the name it was written with. */
  @Test
  fun kernelName_stripsTheWorkspaceEnvsContainer() {
    assertEquals("stats", PythonEnvironment("envs/stats", hasIpykernel = true).kernelName)
  }

  /** `.local/share/virtualenvs` is the deepest container root; its child is still named by leaf. */
  @Test
  fun kernelName_stripsTheDeepVirtualenvsRoot() {
    assertEquals("f", PythonEnvironment(".local/share/virtualenvs/f", hasIpykernel = true).kernelName)
  }

  /**
   * A project's env is named by its whole path with `/` folded to `-`: `proj-a/.venv` and
   * `proj-b/.venv` would both answer `.venv` on the leaf rule, and one spec name cannot serve two
   * interpreters.
   */
  @Test
  fun kernelName_qualifiesAnEnvInsideAProjectByTheProjectPath() {
    assertEquals("proj-a-.venv", PythonEnvironment("proj-a/.venv", hasIpykernel = true).kernelName)
    assertEquals("proj-b-.venv", PythonEnvironment("proj-b/.venv", hasIpykernel = true).kernelName)
    assertEquals(".venv", PythonEnvironment(".venv", hasIpykernel = true).kernelName)
  }

  /** `envs` inside a project is a path segment, not the workspace container: the names stay apart. */
  @Test
  fun kernelName_keepsAContainerSegmentThatIsNotLeading() {
    assertEquals("proj-a-envs-stats", PythonEnvironment("proj-a/envs/stats", hasIpykernel = true).kernelName)
    assertEquals("proj-a-stats", PythonEnvironment("proj-a/stats", hasIpykernel = true).kernelName)
  }

  /** The fold runs over the joined path, so a refused character in any segment becomes `-`. */
  @Test
  fun kernelName_foldsWhatTheSpecNamePatternRefusesAcrossTheWholePath() {
    assertEquals("proj-a-my-env", PythonEnvironment("proj-a/my env", hasIpykernel = true).kernelName)
  }

  /** `_kernel_name_pat` is `^[a-z0-9._\-]+$`: a directory skipped for any other character is a lost kernel. */
  @Test
  fun kernelName_foldsWhatTheSpecNamePatternRefusesToADash() {
    assertEquals("my-env-1", PythonEnvironment("envs/my env+1", hasIpykernel = true).kernelName)
  }

  @Test
  fun kernelName_keepsTheDotOfAHiddenVenv() {
    assertEquals(".venv", PythonEnvironment(".venv", hasIpykernel = true).kernelName)
  }

  /** As `ipykernel install --display-name` is conventionally used: the language, then the env. */
  @Test
  fun kernelDisplayName_namesTheEnvironmentsDirectory() {
    assertEquals("Python (stats)", PythonEnvironment("envs/stats", hasIpykernel = true).kernelDisplayName)
  }

  /** The picker must say which project an env belongs to, so the display keeps the whole path. */
  @Test
  fun kernelDisplayName_showsTheProjectAnEnvInsideOneBelongsTo() {
    assertEquals(
      "Python (proj-a/.venv)",
      PythonEnvironment("proj-a/.venv", hasIpykernel = true).kernelDisplayName,
    )
    assertEquals(
      "Python (proj-a/envs/stats)",
      PythonEnvironment("proj-a/envs/stats", hasIpykernel = true).kernelDisplayName,
    )
  }

  /** The Space's own jupyter_client CLI, which finds the spec on the same path its server does. */
  @Test
  fun kernelRemoveCommand_removesTheSpecByTheNameItIsRegisteredAs() {
    val gone = PythonEnvironment("envs/stats", hasIpykernel = false, registeredAs = "stats", isMissing = true)

    assertEquals("jupyter kernelspec remove stats", gone.kernelRemoveCommand)
  }

  /** Its spec names an interpreter that is gone, so a second spec for it would start nothing either. */
  @Test
  fun registrableEnvironment_refusesAnEnvironmentThatIsGone() {
    val gone = PythonEnvironment("envs/stats", hasIpykernel = true, registeredAs = "stats", isMissing = true)

    val refusal = runCatching { registrableEnvironment(listOf(gone), gone.path, emptyMap()) }.exceptionOrNull()

    assertTrue("must say it is gone, was '${refusal?.message}'", refusal?.message?.contains("gone") == true)
  }

  /** The scan's project folders and the panel's project sections are one rule, so it lives here. */
  @Test
  fun isProjectFolderName_takesAnOrdinaryTopLevelDirectory() {
    assertTrue("proj-a".isProjectFolderName())
    assertTrue("My Project".isProjectFolderName())
  }

  @Test
  fun isProjectFolderName_refusesWhatIsHiddenOrAnEnvContainer() {
    assertFalse(".config".isProjectFolderName())
    for (container in listOf("envs", "Envs", ".direnv", ".venvs", ".virtualenvs", ".local")) {
      assertFalse("'$container' is no project folder", container.isProjectFolderName())
    }
  }
}
