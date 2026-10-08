package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceEnvironmentsContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeSpaceEnvironmentsContractTest : SpaceEnvironmentsContract() {
  override fun environments(): SpaceEnvironments = FakeSpaceEnvironments(mapOf(EMPTY_SPACE_ID to emptyList()))

  @Test
  fun environments_answersWhatItWasSeededWith() = runTest {
    val stats = PythonEnvironment("envs/stats", hasIpykernel = true)

    assertEquals(listOf(stats), FakeSpaceEnvironments(mapOf("base" to listOf(stats))).environments("base"))
  }
}
