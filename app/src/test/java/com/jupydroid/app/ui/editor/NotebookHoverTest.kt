package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageMarkup
import com.jupydroid.app.domain.port.LanguageMarkupKind
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's "Symbol info" ask, answered by the Space's language server
 * at the caret's **virtual** position - the same document completions and diagnostics already
 * share, so `import os` in an earlier cell is what makes `os.path`'s
 * hover answerable in this one.
 *
 * The session gate is [NotebookKernel.hoverAt]'s own rule carried over: a notebook that never
 * ran a cell never asked for a Space, so a menu row there asks nothing of anyone. Unlike
 * [NotebookCompletion] there is no kernel turn - `inspect_request` is a different feature the
 * plan never promised, and a server that cannot answer is simply nothing to show.
 */
class NotebookHoverTest {

  private lateinit var server: RecordingHoverServer
  private lateinit var session: NotebookKernelSession
  private lateinit var hover: NotebookHover

  @Before
  fun setUp() {
    server = RecordingHoverServer()
    val gateway: KernelGateway = FakeKernelGateway()
    session = NotebookKernelSession(gateway, BASE_SPACE) { "Analysis.ipynb" }
    hover = NotebookHover(session, server, NotebookLanguageDocument(server, BASE_SPACE) { "Analysis.ipynb" })
  }

  /**
   * The entered Space's `pylsp` answers the hover, never base's - `StdioLanguageServer`
   * runs one per Space.
   */
  @Test
  fun theAskGoesToTheDocumentsSpace() = runTest {
    val recording = SpaceRecordingLanguageServer(server)
    val asked =
      NotebookHover(session, recording, NotebookLanguageDocument(recording, THESIS_SPACE) { "Analysis.ipynb" })
    session.open()

    asked.at(cells("cell-1"), "cell-1", "compute_total(values)", 3)

    assertEquals(setOf(THESIS_SPACE.id), recording.spaceIds.toSet())
  }

  @Test
  fun noKernel_answersNothingAndAsksNobody() = runTest {
    assertEquals(null, hover.at(cells("cell-1"), "cell-1", "print(1)", 1))
    assertTrue("the language server must not be started by a menu row alone", server.syncs.isEmpty())
  }

  @Test
  fun withAKernel_theServersAnswerComesBack() = runTest {
    session.open()
    server.answer = HOVER

    val answer = hover.at(cells("cell-1"), "cell-1", "compute_total(values)", 3)

    assertEquals(HOVER, answer)
  }

  @Test
  fun theCursor_asksAtItsVirtualPosition_pastTheCellsBeforeIt() = runTest {
    session.open()
    val cells =
      listOf(
        Cell("cell-1", CellKind.CODE, "def compute_total(values):\n    return sum(values)"),
        Cell("cell-2", CellKind.CODE, "compute_total([1, 2])"),
      )

    hover.at(cells, "cell-2", "compute_total([1, 2])", 3)

    // cell-1 is two lines; two separators later cell-2's offset 3 is virtual (4, 3).
    assertEquals(LanguagePosition(line = 4, character = 3), server.lastPosition)
  }

  /** The field's live text is what is synced, never `uiState`'s up-to-a-debounce-old copy. */
  @Test
  fun theAsk_syncsTheCellsLiveSource() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.CODE, "x = 1"))

    hover.at(cells, "cell-1", "x = 12", 6)

    assertEquals("x = 12\n", server.syncs.single().text)
  }

  /** A markdown cell has no block in the Python document - `positionOf` answers null for it. */
  @Test
  fun aCellOutsideTheVirtualDocument_answersNothing() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.MARKDOWN, "# notes"))

    assertEquals(null, hover.at(cells, "cell-1", "# notes", 2))
    assertTrue(server.hovers.isEmpty())
  }

  /** A `pylsp` that cannot answer is nothing to show, not a crash - completion's own rule. */
  @Test
  fun aServerThatCannotAnswer_isSilence() = runTest {
    session.open()
    server.failure = IllegalStateException("pylsp exited")

    assertEquals(null, hover.at(cells("cell-1"), "cell-1", "print(1)", 1))
  }

  @Test
  fun aCancelledAsk_staysCancelled() = runTest {
    session.open()
    server.failure = CancellationException("the cell was dismissed")

    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking { hover.at(cells("cell-1"), "cell-1", "print(1)", 1) }
    }
  }

  private fun cells(vararg ids: String) = ids.map { Cell(it, CellKind.CODE, "x = 1") }

  /** Records the syncs and asks the adapter would hear, and can fail like a dead `pylsp`. */
  private class RecordingHoverServer : LanguageServerPort by FakeLanguageServerPort() {
    data class Sync(val uri: String, val text: String)

    val syncs = mutableListOf<Sync>()
    val hovers = mutableListOf<LanguagePosition>()
    var lastPosition: LanguagePosition? = null
      private set
    var failure: Exception? = null
    var answer: LanguageHover? = null

    override suspend fun changeDocument(space: Space, uri: String, text: String) {
      syncs += Sync(uri, text)
    }

    override suspend fun hover(space: Space, uri: String, position: LanguagePosition): LanguageHover? {
      lastPosition = position
      hovers += position
      failure?.let { throw it }
      return answer
    }
  }

  private companion object {
    val HOVER = LanguageHover(LanguageMarkup(LanguageMarkupKind.MARKDOWN, "compute_total(values)"))
  }
}
