package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageDiagnostic
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.PublishDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `docs/PLAN.md` P5d-2b: `publishDiagnostics` publications mapped back through the mirror into
 * per-cell marks, and the mirror kept synced with the cells while a kernel session exists -
 * the same gate [NotebookCompletion] keeps, so watching never boots a runtime either.
 *
 * The collectors run on a real dispatcher because `waitUntil`'s `Thread.sleep` blocks a
 * `runBlocking` event loop - the same trap `aRequestCancelledByItsCaller_sendsCancelRequest`
 * documented in `StdioLanguageServerTest`.
 */
class NotebookDiagnosticsTest {

  private val scope = CoroutineScope(Dispatchers.Default)
  private lateinit var uiState: MutableStateFlow<EditorUiState>
  private lateinit var server: RecordingSyncServer
  private lateinit var document: NotebookLanguageDocument
  private lateinit var diagnostics: NotebookDiagnostics

  private val uri = "file:///jupyterdroid/notebooks/Analysis.ipynb.py"

  @Before
  fun setUp() {
    uiState = MutableStateFlow(EditorUiState(cells = listOf(code("c1", "x = 1"), code("c2", "print(undef_name)"))))
    server = RecordingSyncServer()
    document = NotebookLanguageDocument(server) { "Analysis.ipynb" }
    diagnostics = NotebookDiagnostics(uiState, server, document, scope)
  }

  @Test
  fun aPublishOnTheNotebookUri_landsOnTheCellAndLineItNames() = runBlocking {
    connect()
    // c1 owns virtual line 0; two blank separators later c2 starts on virtual line 3.
    publish(diagnostic(LanguagePosition(3, 6), LanguagePosition(3, 15), "undefined name 'undef_name'"))

    waitUntil { uiState.value.diagnosticsByCellId["c2"]?.isNotEmpty() == true }
    val flagged = uiState.value.diagnosticsByCellId.getValue("c2").single()
    assertEquals(1, flagged.line)
    assertEquals(1, flagged.severity)
    // `undef_name` sits at offsets 6..15 inside "print(undef_name)" - the squiggle's span.
    assertEquals(6, flagged.start)
    assertEquals(15, flagged.end)
    assertEquals("undefined name 'undef_name'", flagged.message)
    assertEquals("F821", flagged.code)
    assertEquals("pyflakes", flagged.source)
  }

  @Test
  fun aPublishForAnotherUri_isIgnored() = runBlocking {
    connect()

    server.emitDiagnostics(PublishDiagnostics("file:///elsewhere.py", listOf(someDiagnostic())))

    giveThePushTimeToLand()
    assertTrue(uiState.value.diagnosticsByCellId.isEmpty())
  }

  @Test
  fun anEmptyPublish_clearsTheMarksTheServerWithdrew() = runBlocking {
    connect()
    publish(someDiagnostic())
    waitUntil { uiState.value.diagnosticsByCellId.isNotEmpty() }

    publish()

    waitUntil { uiState.value.diagnosticsByCellId.isEmpty() }
  }

  @Test
  fun aDiagnosticOnASeparatorLine_isDroppedRatherThanLandedOnTheWrongCell() = runBlocking {
    connect()
    // Virtual line 1 is the blank separator between c1's block and c2's - it belongs to no cell.
    publish(diagnostic(LanguagePosition(1, 0), LanguagePosition(1, 1)))

    giveThePushTimeToLand()
    assertTrue(uiState.value.diagnosticsByCellId.isEmpty())
  }

  @Test
  fun aPublishBeforeAnySync_mapsAgainstTheLatestSyncOnceItLands() = runBlocking {
    diagnostics.start()
    uiState.value = uiState.value.copy(kernelConnected = true)
    waitUntil { server.syncs.isNotEmpty() }
    waitUntil { server.diagnosticSubscriberCount.value > 0 }

    publish(diagnostic(LanguagePosition(0, 0), LanguagePosition(0, 1)))

    waitUntil { uiState.value.diagnosticsByCellId["c1"]?.isNotEmpty() == true }
    assertEquals(1, uiState.value.diagnosticsByCellId.getValue("c1").single().line)
  }

  @Test
  fun editsWhileNoKernelIsConnected_syncNothingAndBootNothing() = runBlocking {
    diagnostics.start()

    uiState.value = uiState.value.withSource("c1", "y = 2")
    Thread.sleep(150)

    assertTrue("watching edits must not boot the language server", server.syncs.isEmpty())
  }

  @Test
  fun aKernelConnecting_syncsTheCurrentCellsWithoutAKeystroke() = runBlocking {
    diagnostics.start()

    uiState.value = uiState.value.copy(kernelConnected = true)

    waitUntil { server.syncs.isNotEmpty() }
    assertEquals("x = 1\n\n\nprint(undef_name)\n", server.syncs.last().text)
  }

  @Test
  fun editsWhileAKernelIsConnected_resyncTheWholeDocument() = runBlocking {
    connect()

    uiState.value = uiState.value.withSource("c2", "print(other_name)")

    waitUntil { server.syncs.size >= 2 }
    assertEquals("x = 1\n\n\nprint(other_name)\n", server.syncs.last().text)
  }

  /**
   * Open the kernel-side gate, let the first sync land, and wait for the relay's subscription -
   * the stream is hot, so an emission made before it listens is gone.
   */
  private suspend fun connect() {
    diagnostics.start()
    uiState.value = uiState.value.copy(kernelConnected = true)
    waitUntil { server.syncs.isNotEmpty() }
    waitUntil { server.diagnosticSubscriberCount.value > 0 }
  }

  private suspend fun publish(vararg items: LanguageDiagnostic) {
    server.emitDiagnostics(PublishDiagnostics(uri, items.toList()))
  }

  /** Where nothing is expected, a beat for a push to have had its chance and been ignored. */
  private fun giveThePushTimeToLand() = Thread.sleep(150)

  private fun someDiagnostic() = diagnostic(LanguagePosition(3, 0), LanguagePosition(3, 5))

  private fun diagnostic(start: LanguagePosition, end: LanguagePosition, message: String = "undefined name 'x'") =
    LanguageDiagnostic(
      range = LanguageRange(start, end),
      severity = 1,
      code = "F821",
      source = "pyflakes",
      message = message,
    )

  private fun waitUntil(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!condition()) {
      check(System.currentTimeMillis() < deadline) { "condition never arrived" }
      Thread.sleep(10)
    }
  }

  /** The syncs the adapter would hear plus the push surface the fake already owns. */
  private class RecordingSyncServer(private val fake: FakeLanguageServerPort = FakeLanguageServerPort()) :
    LanguageServerPort by fake {
    data class Sync(val uri: String, val text: String)

    val syncs = mutableListOf<Sync>()
    val diagnosticSubscriberCount get() = fake.diagnosticSubscriberCount

    suspend fun emitDiagnostics(publish: PublishDiagnostics) = fake.emitDiagnostics(publish)

    override suspend fun changeDocument(space: Space, uri: String, text: String) {
      syncs += Sync(uri, text)
    }
  }

  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)
}
