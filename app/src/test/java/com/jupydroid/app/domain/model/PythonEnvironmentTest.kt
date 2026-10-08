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
}
