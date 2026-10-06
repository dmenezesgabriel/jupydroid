package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's format ask - `textDocument/rangeFormatting` over the cell's
 * whole virtual block, answered (by the shipped `pylsp`) as **one whole-document `TextEdit`**
 * and sliced back to the cell's own source through the prefix/suffix guard.
 *
 * The session gate is the asks' shared rule (`NotebookAsks`'s own doc): a notebook that never
 * ran a cell never asked for a Space, so "Format cell" there must not boot one. A refused or
 * failed answer is silence, not a half-applied notebook.
 */
class NotebookFormatTest {

  private lateinit var server: RecordingFormatServer
  private lateinit var session: NotebookKernelSession
  private lateinit var format: NotebookFormat

  @Before
  fun setUp() {
    server = RecordingFormatServer()
    val gateway: KernelGateway = FakeKernelGateway()
    session = NotebookKernelSession(gateway, BASE_SPACE) { "Analysis.ipynb" }
    format =
      NotebookFormat(session, server, NotebookLanguageDocument(server, BASE_SPACE) { "Analysis.ipynb" }, { OPTIONS })
  }

  /**
   * The entered Space's `pylsp` answers the range format, never base's - `StdioLanguageServer`
   * runs one per Space.
   */
  @Test
  fun theAskGoesToTheDocumentsSpace() = runTest {
    val recording = SpaceRecordingLanguageServer(server)
    val asked =
      NotebookFormat(
        session,
        recording,
        NotebookLanguageDocument(recording, THESIS_SPACE) {
          "Analysis.ipynb"
        },
        { OPTIONS },
      )
    session.open()

    asked.formatted(cells(), "cell-2", "x=1")

    assertEquals(setOf(THESIS_SPACE.id), recording.spaceIds.toSet())
  }

  @Test
  fun noKernel_answersNothingAndAsksNobody() = runTest {
    assertNull(format.formatted(cells(), "cell-2", "x=1"))
    assertTrue("typing alone must not start the language server", server.syncs.isEmpty())
    assertTrue(server.asks.isEmpty())
  }

  /** The cell's whole virtual block is what is asked - past the cells and separators before it. */
  @Test
  fun theCell_asksForItsWholeVirtualRange() = runTest {
    session.open()
    server.answer = emptyList()

    format.formatted(cells(), "cell-2", "x=1")

    // cell-1 is two lines; two separators later cell-2's one live line is virtual 4.
    assertEquals(LanguageRange(LanguagePosition(4, 0), LanguagePosition(4, 3)), server.lastRange)
  }

  /** The field's live text is what is synced, never `uiState`'s up-to-a-debounce-old copy. */
  @Test
  fun theAsk_syncsTheCellsLiveSource() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.CODE, "x = 1"))

    format.formatted(cells, "cell-1", "x=1")

    assertEquals("x=1\n", server.syncs.single().text)
  }

  /** The editor's own indentation setting is the `options` the wire requires - sent verbatim. */
  @Test
  fun theAsk_sendsTheEditorsOwnFormattingOptions() = runTest {
    session.open()
    val format = NotebookFormat(
      session,
      server,
      NotebookLanguageDocument(server, BASE_SPACE) { "Analysis.ipynb" },
      { LanguageFormatOptions(tabSize = 2, insertSpaces = true) },
    )

    format.formatted(cells(), "cell-2", "x=1")

    assertEquals(LanguageFormatOptions(tabSize = 2, insertSpaces = true), server.lastOptions)
  }

  /** autopep8's own reply shape: one whole-document edit, sliced back to the asked cell. */
  @Test
  fun aWholeDocumentAnswer_becomesTheCellsFormattedSource() = runTest {
    session.open()
    // Synced text is "x = 1\nx=1\n\n\nx=1\nx = 2\n" (six splitlines); only cell-2's lines change.
    server.answer = listOf(edit(0, 0, 6, 0, "x = 1\nx=1\n\n\nx = 1\nx = 2\n"))

    val source = format.formatted(cells(), "cell-2", "x=1\nx = 2")

    assertEquals("x = 1\nx = 2", source)
  }

  /** `[]` - the cell was already formatted - is a null, not a rewrite of itself. */
  @Test
  fun anEmptyReply_isNothingToApply() = runTest {
    session.open()
    server.answer = emptyList()

    assertNull(format.formatted(cells(), "cell-2", "x=1"))
  }

  /** An answer that touched a separator or another cell is refused rather than half-applied. */
  @Test
  fun anEditThatMovedOutsideTheCell_isSilence() = runTest {
    session.open()
    // The formatter "fixed" cell-1's second line too - the prefix guard refuses it.
    server.answer = listOf(edit(0, 0, 6, 0, "x = 1\nx = 1\n\n\nx = 1\nx = 2\n"))

    assertNull(format.formatted(cells(), "cell-2", "x=1\nx = 2"))
  }

  /** A markdown cell owns no block in the Python document - `rangeOf` answers null for it. */
  @Test
  fun aCellOutsideTheVirtualDocument_answersNothing() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.MARKDOWN, "# notes"))

    assertNull(format.formatted(cells, "cell-1", "# notes"))
    assertTrue(server.asks.isEmpty())
  }

  /** A `pylsp` that cannot answer is silence, not a crash - the asks' own rule. */
  @Test
  fun aServerThatCannotAnswer_isSilence() = runTest {
    session.open()
    server.failure = IllegalStateException("pylsp exited")

    assertNull(format.formatted(cells(), "cell-2", "x=1"))
  }

  @Test
  fun aCancelledAsk_staysCancelled() = runTest {
    session.open()
    server.failure = CancellationException("the ask was superseded")

    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking { format.formatted(cells(), "cell-2", "x=1") }
    }
  }

  /** cell-1 two lines, a markdown cell owning no block, and cell-2's own two lines. */
  private fun cells() = listOf(
    Cell("cell-1", CellKind.CODE, "x = 1\nx=1"),
    Cell("cell-2", CellKind.CODE, "x=1\nx = 2"),
    Cell("cell-3", CellKind.MARKDOWN, "# notes"),
  )

  private fun edit(startLine: Int, startChar: Int, endLine: Int, endChar: Int, newText: String) =
    LanguageEdit(LanguageRange(LanguagePosition(startLine, startChar), LanguagePosition(endLine, endChar)), newText)

  /** Records the syncs, ranges and options the adapter would hear, and can fail like a dead `pylsp`. */
  private class RecordingFormatServer : LanguageServerPort by FakeLanguageServerPort() {
    data class Sync(val uri: String, val text: String)

    val syncs = mutableListOf<Sync>()
    val asks = mutableListOf<LanguageRange>()
    var lastRange: LanguageRange? = null
      private set
    var lastOptions: LanguageFormatOptions? = null
      private set
    var failure: Exception? = null
    var answer: List<LanguageEdit> = emptyList()

    override suspend fun changeDocument(space: Space, uri: String, text: String) {
      syncs += Sync(uri, text)
    }

    override suspend fun rangeFormatting(
      space: Space,
      uri: String,
      range: LanguageRange,
      options: LanguageFormatOptions,
    ): List<LanguageEdit> {
      lastRange = range
      lastOptions = options
      asks += range
      failure?.let { throw it }
      return answer
    }
  }

  private companion object {
    val OPTIONS = LanguageFormatOptions(tabSize = 4, insertSpaces = true)
  }
}
