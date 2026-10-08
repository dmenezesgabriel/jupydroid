package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
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
}
