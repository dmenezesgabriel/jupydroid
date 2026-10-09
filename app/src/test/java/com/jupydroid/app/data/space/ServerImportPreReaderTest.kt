package com.jupydroid.app.data.space

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * ADR-0081's obligations, one per test: the pre-reads go one Space at a time with base first, skip a
 * Space not installed or already running, survive a failed one, and say in the log when each ran.
 */
class ServerImportPreReaderTest {
  private val log = RecordingSpaceLifecycleLog()
  private val running = MutableStateFlow(emptySet<String>())

  @Test
  fun preReadAll_importsEveryInstalledSpaceInOrder() = runTest {
    val import = FakeServerImport()

    reader(listOf("base", "space-1", "space-2"), import).preReadAll()

    assertEquals(listOf("base", "space-1", "space-2"), import.imported)
  }

  @Test
  fun preReadAll_skipsASpaceThatIsNotInstalled() = runTest {
    val import = FakeServerImport()

    reader(listOf("base", "space-1"), import, installed = setOf("space-1")).preReadAll()

    assertEquals(listOf("space-1"), import.imported)
  }

  /** Checked at that Space's turn, so a Space run while an earlier one was pre-read is skipped too. */
  @Test
  fun preReadAll_skipsASpaceWhoseServerIsRunningWhenItsTurnComes() = runTest {
    val import = FakeServerImport(onImport = { id -> if (id == "base") running.value = setOf("space-1") })

    reader(listOf("base", "space-1", "space-2"), import).preReadAll()

    assertEquals(listOf("base", "space-2"), import.imported)
  }

  @Test
  fun preReadAll_logsAFailedPreReadAndGoesOnToTheNextSpace() = runTest {
    val import = FakeServerImport(failing = setOf("base"))

    reader(listOf("base", "space-1"), import).preReadAll()

    assertEquals(listOf("base", "space-1"), import.imported)
    assertEquals(listOf(ServerImportPreReader.PRE_READ_FAILED), log.failures)
  }

  @Test
  fun preReadAll_reportsANonZeroExitInTheEndLine() = runTest {
    val import = FakeServerImport(exitCodes = mapOf("base" to 1))

    reader(listOf("base"), import).preReadAll()

    assertEquals("1", log.fieldsOf(ServerImportPreReader.PRE_READ_FINISHED)["exitCode"])
  }

  @Test
  fun preReadAll_logsTheStartAndEndOfEachPreRead() = runTest {
    reader(listOf("base", "space-1"), FakeServerImport()).preReadAll()

    val start = ServerImportPreReader.PRE_READ_STARTED
    val end = ServerImportPreReader.PRE_READ_FINISHED
    assertEquals(listOf(start, end, start, end), log.stages)
    assertEquals("base", log.fieldsOf(start)["space"])
    assertEquals(setOf("space", "exitCode", "elapsedMs"), log.fieldsOf(end).keys)
  }

  @Test
  fun preReadAll_neverRunsTwoImportsAtOnce() = runTest {
    val import = FakeServerImport()

    reader(listOf("base", "space-1", "space-2"), import).preReadAll()

    assertEquals(1, import.mostAtOnce)
  }

  private fun reader(ids: List<String>, import: FakeServerImport, installed: Set<String> = ids.toSet()) =
    ServerImportPreReader(
      spaceIds = { ids },
      isInstalled = { it in installed },
      running = running,
      importServer = import::run,
      log = log,
    )
}

/**
 * Stands in for `import jupyter_server.serverapp` in a Space: records which Space it ran in, answers
 * the exit code it was given, and throws for a Space in [failing], as a process that cannot start does.
 */
class FakeServerImport(
  private val failing: Set<String> = emptySet(),
  private val exitCodes: Map<String, Int> = emptyMap(),
  private val onImport: (String) -> Unit = {},
) {
  val imported = mutableListOf<String>()

  /** The most imports that were ever inside [run] at the same moment. */
  var mostAtOnce = 0
    private set

  private var inFlight = 0

  suspend fun run(spaceId: String): ProcessOutcome {
    imported += spaceId
    inFlight += 1
    mostAtOnce = maxOf(mostAtOnce, inFlight)
    yield()
    inFlight -= 1
    onImport(spaceId)
    if (spaceId in failing) throw IOException("cannot run python3 in '$spaceId'")
    return ProcessOutcome(exitCode = exitCodes[spaceId] ?: 0, output = "")
  }
}
