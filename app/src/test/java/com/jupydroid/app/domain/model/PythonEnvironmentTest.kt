package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
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
}
