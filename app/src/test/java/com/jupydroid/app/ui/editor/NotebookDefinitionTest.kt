package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageLocation
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's definition ask, answered by the Space's language server at
 * the caret's **virtual** position and mapped back through the same virtual document -
 * `def compute_total` in an earlier cell is what makes `compute_total` jumpable in this one.
 *
 * The session gate is shared with hover and completion: typing alone never boots a runtime,
 * a server that cannot answer is silence rather than a crash, and a cancelled ask stays
 * cancelled rather than surface as an error.
 */
class NotebookDefinitionTest {

  private lateinit var server: RecordingDefinitionServer
  private lateinit var session: NotebookKernelSession
  private lateinit var definition: NotebookDefinition

  @Before
  fun setUp() {
    server = RecordingDefinitionServer()
    val gateway: KernelGateway = FakeKernelGateway()
    session = NotebookKernelSession(gateway, BASE_SPACE) { "Analysis.ipynb" }
    definition = NotebookDefinition(session, server, NotebookLanguageDocument(server) { "Analysis.ipynb" })
  }

  @Test
  fun noKernel_answersNothingAndAsksNobody() = runTest {
    assertEquals(null, definition.at(cells("cell-1"), "cell-1", "x = 1", 0))
    assertTrue("typing alone must not start the language server", server.syncs.isEmpty())
  }

  /** The caret asks at its virtual position, past the cells before it - hover's own contract. */
  @Test
  fun theCursor_asksAtItsVirtualPosition_pastTheCellsBeforeIt() = runTest {
    session.open()
    val cells =
      listOf(
        Cell("cell-1", CellKind.CODE, "import os"),
        Cell("cell-2", CellKind.CODE, "os.path.join("),
      )

    definition.at(cells, "cell-2", "os.path.join(", 13)

    // cell-1 is one line; two separators later cell-2's caret is virtual (3, 13).
    assertEquals(LanguagePosition(line = 3, character = 13), server.lastPosition)
  }

  /** A location in the document maps to the cell and the offsets inside its own source. */
  @Test
  fun aSameDocumentLocation_mapsIntoTheDefiningCell() = runTest {
    session.open()
    server.answer = listOf(location(DOCUMENT_URI, 0, 4, 0, 17))

    val target = definition.at(cells(), "cell-2", "total = compute_total([1, 2])", 8)

    // cell-1's first line: `def compute_total(items):` - `compute_total` sits at 4..17.
    assertEquals(DefinitionTarget("cell-1", 4, 17), target)
  }

  /** A target in a later cell lands in *that* cell - the cross-cell jump the plan promises. */
  @Test
  fun aLocationInALaterCell_landsInThatCell() = runTest {
    session.open()
    server.answer = listOf(location(DOCUMENT_URI, 7, 4, 7, 17))

    val target = definition.at(cells(), "cell-2", "print(compute_total)", 7)

    // cell-4's first line: `def compute_total(items):` - virtual line 7 is its line 0.
    assertEquals(DefinitionTarget("cell-4", 4, 17), target)
  }

  /**
   * A stdlib `Location` is not the notebook's document - jupyterlab-lsp opens it in a second
   * editor and nothing here stands in for a foreign file, so it is skipped; the first
   * location that does map is the jump.
   */
  @Test
  fun aForeignUri_isSkipped_forTheFirstInDocumentLocation() = runTest {
    session.open()
    server.answer =
      listOf(
        location("file:///usr/lib/python3.11/posixpath.py", 0, 4, 0, 10),
        location(DOCUMENT_URI, 0, 4, 0, 17),
      )

    val target = definition.at(cells(), "cell-2", "total = compute_total([1, 2])", 8)

    assertEquals(DefinitionTarget("cell-1", 4, 17), target)
  }

  /**
   * The regression the first device run caught: a title with a space makes a `Location.uri`
   * arrive **percent-encoded** - pylsp's `uris.uri_with` `quote()`s the path it returns - so
   * the virtual document's uri has to be the same encoding, or every same-document answer
   * reads as off-document and the jump goes nowhere.
   */
  @Test
  fun anEncodedUri_fromAWhitespaceTitle_mapsIntoTheDefiningCell() = runTest {
    session.open()
    val definition = NotebookDefinition(
      session,
      server,
      NotebookLanguageDocument(server) { "Go to Definition.ipynb" },
    )
    server.answer =
      listOf(location("file:///jupyterdroid/notebooks/Go%20to%20Definition.ipynb.py", 0, 4, 0, 17))

    val target = definition.at(cells(), "cell-2", "total = compute_total([1, 2])", 8)

    assertEquals(DefinitionTarget("cell-1", 4, 17), target)
  }

  /** A list of nothing but foreign uris - pylsp's real `os.path.join` answer - is a miss. */
  @Test
  fun onlyForeignUris_isNothingToJumpTo() = runTest {
    session.open()
    server.answer = listOf(location("file:///usr/lib/python3.11/posixpath.py", 0, 4, 0, 10))

    assertEquals(null, definition.at(cells(), "cell-2", "os.path.join(x)", 3))
  }

  /** A range whose start sits on the two blank separator lines belongs to no cell. */
  @Test
  fun aLocationOnASeparator_isNothingToJumpTo() = runTest {
    session.open()
    server.answer = listOf(location(DOCUMENT_URI, 3, 0, 3, 5))

    assertEquals(null, definition.at(cells(), "cell-2", "total = compute_total([1, 2])", 8))
  }

  /** A `def`'s range end may run onto the separator - the flash clamps to the cell's own end. */
  @Test
  fun aRangeEndingOnASeparator_clampsToTheCellsEnd() = runTest {
    session.open()
    server.answer = listOf(location(DOCUMENT_URI, 0, 4, 3, 0))

    val target = definition.at(cells(), "cell-2", "total = compute_total([1, 2])", 8)

    assertEquals(DefinitionTarget("cell-1", 4, "def compute_total(items):\n    return sum(items)".length), target)
  }

  @Test
  fun anEmptyReply_isNothingToJumpTo() = runTest {
    session.open()
    server.answer = emptyList()

    assertEquals(null, definition.at(cells(), "cell-2", "total = compute_total([1, 2])", 8))
  }

  /** A markdown cell has no block in the Python document - `positionOf` answers null for it. */
  @Test
  fun aCellOutsideTheVirtualDocument_answersNothing() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.MARKDOWN, "# notes"))

    assertEquals(null, definition.at(cells, "cell-1", "# notes", 2))
    assertTrue(server.asks.isEmpty())
  }

  /** A `pylsp` that cannot answer is nothing to jump to, not a crash - hover's own rule. */
  @Test
  fun aServerThatCannotAnswer_isSilence() = runTest {
    session.open()
    server.failure = IllegalStateException("pylsp exited")

    assertEquals(null, definition.at(cells(), "cell-1", "x = 1", 0))
  }

  @Test
  fun aCancelledAsk_staysCancelled() = runTest {
    session.open()
    server.failure = CancellationException("the ask was superseded")

    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking { definition.at(cells(), "cell-1", "x = 1", 0) }
    }
  }

  /** The field's live text is what is synced, never `uiState`'s up-to-a-debounce-old copy. */
  @Test
  fun theAsk_syncsTheCellsLiveSource() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.CODE, "x = 1"))

    definition.at(cells, "cell-1", "compute_total([1])", 4)

    assertEquals("compute_total([1])\n", server.syncs.single().text)
  }

  /**
   * `def compute_total` in cell-1, a call in cell-2, a markdown cell that owns no block,
   * and cell-4's own `def` - the notebook a jump crosses.
   */
  private fun cells(vararg ids: String = emptyArray()) = if (ids.isNotEmpty()) {
    ids.map { Cell(it, CellKind.CODE, "x = 1") }
  } else {
    listOf(
      Cell("cell-1", CellKind.CODE, "def compute_total(items):\n    return sum(items)"),
      Cell("cell-2", CellKind.CODE, "total = compute_total([1, 2])"),
      Cell("cell-3", CellKind.MARKDOWN, "# notes"),
      Cell("cell-4", CellKind.CODE, "def compute_total(items):\n    return sum(items)"),
    )
  }

  private fun location(uri: String, startLine: Int, startChar: Int, endLine: Int, endChar: Int) = LanguageLocation(
    uri,
    LanguageRange(LanguagePosition(startLine, startChar), LanguagePosition(endLine, endChar)),
  )

  /** Records the syncs and asks the adapter would hear, and can fail like a dead `pylsp`. */
  private class RecordingDefinitionServer : LanguageServerPort by FakeLanguageServerPort() {
    data class Sync(val uri: String, val text: String)

    val syncs = mutableListOf<Sync>()
    val asks = mutableListOf<LanguagePosition>()
    var lastPosition: LanguagePosition? = null
      private set
    var failure: Exception? = null
    var answer: List<LanguageLocation> = emptyList()

    override suspend fun changeDocument(space: Space, uri: String, text: String) {
      syncs += Sync(uri, text)
    }

    override suspend fun definition(space: Space, uri: String, position: LanguagePosition): List<LanguageLocation> {
      lastPosition = position
      asks += position
      failure?.let { throw it }
      return answer
    }
  }

  private companion object {
    /** The virtual document's own uri - `NotebookVirtualDocument`'s shape for `Analysis.ipynb`. */
    const val DOCUMENT_URI = "file:///jupyterdroid/notebooks/Analysis.ipynb.py"
  }
}
