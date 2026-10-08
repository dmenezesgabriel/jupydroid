package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.PythonEnvironment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [SpaceEnvironments] must answer, run against the fake and the disk adapter alike so
 * a ViewModel test against the fake cannot pass on a behaviour no real Space has.
 */
abstract class SpaceEnvironmentsContract {
  /** An implementation holding one Space, [EMPTY_SPACE_ID], with [environments] and no other in it. */
  protected abstract fun environmentsHolding(vararg environments: PythonEnvironment): SpaceEnvironments

  /** An implementation holding one Space, [EMPTY_SPACE_ID], with no environment in it. */
  protected fun environments(): SpaceEnvironments = environmentsHolding()

  @Test
  fun environments_ofASpaceWithNoneIsEmpty() = runTest {
    assertEquals(emptyList<Any>(), environments().environments(EMPTY_SPACE_ID))
  }

  @Test
  fun environments_ofAnUnknownSpaceIsRefusedNamingIt() = runTest {
    assertRefusedNaming("space-404") { environments().environments("space-404") }
  }

  @Test
  fun register_answersTheKernelNameAndListsTheEnvironmentAsRegisteredUnderIt() = runTest {
    val port = environmentsHolding(STATS)

    assertEquals("stats", port.register(EMPTY_SPACE_ID, STATS.path))
    assertEquals(listOf(STATS.copy(registeredAs = "stats")), port.environments(EMPTY_SPACE_ID))
  }

  /** A spec for an env with no ipykernel names a kernel that can never start. */
  @Test
  fun register_refusesAnEnvironmentWithoutIpykernelGivingTheCommandThatAddsIt() = runTest {
    val bare = PythonEnvironment("envs/bare", hasIpykernel = false)

    assertRefusedNaming(bare.ipykernelInstallCommand) {
      environmentsHolding(bare).register(EMPTY_SPACE_ID, bare.path)
    }
  }

  @Test
  fun register_refusesAPathThatIsNoEnvironmentOfTheSpace() = runTest {
    assertRefusedNaming("envs/nowhere") { environmentsHolding(STATS).register(EMPTY_SPACE_ID, "envs/nowhere") }
  }

  @Test
  fun register_refusesAnUnknownSpace() = runTest {
    assertRefusedNaming("space-404") { environmentsHolding(STATS).register("space-404", STATS.path) }
  }

  /** Two directories called `stats` fold to one name, and the server would list only the first. */
  @Test
  fun register_refusesANameAnotherKernelAlreadyHas() = runTest {
    val otherStats = PythonEnvironment(".venvs/stats", hasIpykernel = true)
    val port = environmentsHolding(STATS, otherStats)
    port.register(EMPTY_SPACE_ID, STATS.path)

    assertRefusedNaming("'stats'") { port.register(EMPTY_SPACE_ID, otherStats.path) }
  }

  /** `python3` is the Space's own kernel, which a registration must never replace. */
  @Test
  fun register_refusesTheNameOfTheSpacesOwnKernel() = runTest {
    val python3 = PythonEnvironment("envs/python3", hasIpykernel = true)

    assertRefusedNaming("'python3'") { environmentsHolding(python3).register(EMPTY_SPACE_ID, python3.path) }
  }

  /** A registered environment deleted since is still listed, as gone, and cannot be registered again. */
  @Test
  fun register_refusesAnEnvironmentThatIsGoneSayingSo() = runTest {
    val port = environmentsHolding(GONE_STATS)

    assertEquals(listOf(GONE_STATS), port.environments(EMPTY_SPACE_ID))
    assertRefusedNaming("gone") { port.register(EMPTY_SPACE_ID, GONE_STATS.path) }
  }

  private suspend fun assertRefusedNaming(expected: String, ask: suspend () -> Unit) {
    val refusal = runCatching { ask() }.exceptionOrNull()

    assertTrue("expected IllegalArgumentException, was $refusal", refusal is IllegalArgumentException)
    assertTrue("message must name $expected, was '${refusal?.message}'", refusal?.message?.contains(expected) == true)
  }

  protected companion object {
    const val EMPTY_SPACE_ID = "space-1"
    val STATS = PythonEnvironment("envs/stats", hasIpykernel = true)
    val GONE_STATS = PythonEnvironment("envs/stats", hasIpykernel = false, registeredAs = "stats", isMissing = true)
  }
}
