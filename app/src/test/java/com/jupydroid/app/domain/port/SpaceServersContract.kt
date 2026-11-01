@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.domain.port

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [SpaceServers] must do, whoever implements it: several Spaces run at once, each
 * stops alone, and [SpaceServers.running] says which are up.
 *
 * Each act is followed by `runCurrent()`, because a real implementation learns of a stop from the
 * process's exit, on a watcher of its own, rather than from the call that asked for it.
 */
abstract class SpaceServersContract {
  protected abstract fun TestScope.servers(): SpaceServers

  @Test
  fun nothingRunsUntilASpaceIsRun() = runTest {
    assertEquals(emptySet<String>(), servers().running.value)
  }

  @Test
  fun aRunSpaceReadsRunning() = runTest {
    val servers = servers()

    servers.run("base")
    runCurrent()

    assertEquals(setOf("base"), servers.running.value)
  }

  @Test
  fun twoSpacesRunAtOnce() = runTest {
    val servers = servers()

    servers.run("base")
    servers.run("space-1")
    runCurrent()

    assertEquals(setOf("base", "space-1"), servers.running.value)
  }

  @Test
  fun stoppingOneSpaceLeavesTheOtherRunning() = runTest {
    val servers = servers()
    servers.run("base")
    servers.run("space-1")

    servers.stop("space-1")
    runCurrent()

    assertEquals(setOf("base"), servers.running.value)
  }

  @Test
  fun stopAllLeavesNothingRunning() = runTest {
    val servers = servers()
    servers.run("base")
    servers.run("space-1")

    servers.stopAll()
    runCurrent()

    assertEquals(emptySet<String>(), servers.running.value)
  }

  @Test
  fun stoppingASpaceThatNeverRanChangesNothing() = runTest {
    val servers = servers()
    servers.run("base")

    servers.stop("space-1")
    runCurrent()

    assertEquals(setOf("base"), servers.running.value)
  }
}
