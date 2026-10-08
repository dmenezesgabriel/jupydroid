package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [SpaceEnvironments] must answer, run against the fake and the disk adapter alike so
 * a ViewModel test against the fake cannot pass on a behaviour no real Space has.
 */
abstract class SpaceEnvironmentsContract {
  /** An implementation holding one Space, [EMPTY_SPACE_ID], with no environment in it. */
  protected abstract fun environments(): SpaceEnvironments

  @Test
  fun environments_ofASpaceWithNoneIsEmpty() = runTest {
    assertEquals(emptyList<Any>(), environments().environments(EMPTY_SPACE_ID))
  }

  @Test
  fun environments_ofAnUnknownSpaceIsRefusedNamingIt() = runTest {
    val refusal = runCatching { environments().environments("space-404") }.exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue(
      "message must name space-404, was '${refusal?.message}'",
      refusal?.message?.contains("space-404") == true,
    )
  }

  protected companion object {
    const val EMPTY_SPACE_ID = "space-1"
  }
}
