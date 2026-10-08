package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The default a Space's server starts with, and the names a kernelspec may have. */
class KernelNameTest {
  @Test
  fun aStoredDefaultTheCatalogueListsIsTheDefault() {
    assertEquals("stats", effectiveDefaultKernel("stats", listOf("python3", "stats")))
  }

  /** A spec deleted after Save: the server would name a kernel nothing can start, so python3 answers. */
  @Test
  fun aStoredDefaultTheCatalogueNoLongerListsFallsBackToPython3() {
    assertEquals(DEFAULT_KERNEL_ID, effectiveDefaultKernel("stats", listOf("python3")))
  }

  @Test
  fun aNameKernelSpecsAllowIsAKernelName() {
    listOf("python3", "stats", "my-env_2.1", "Stats").forEach { assertTrue(it, isKernelName(it)) }
  }

  /** `_kernel_name_pat` skips such a directory, so no spec can ever be listed under the name. */
  @Test
  fun aNameKernelSpecsRefuseIsNoKernelName() {
    listOf("", "my env", "envs/stats", "café").forEach { assertFalse(it, isKernelName(it)) }
  }
}
