package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageParameter
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageSignature
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's signature-help ask, answered by the Space's language server
 * at the caret's **virtual** position - [NotebookHover]'s own contract one method over, so
 * `import os` in an earlier cell is what makes `os.path.join(` answerable in this one.
 *
 * The session gate is shared with hover and completion: typing alone never boots a runtime,
 * a server that cannot answer is silence rather than a crash, and a cancelled ask stays
 * cancelled rather than surface as an error.
 */
class NotebookSignatureHelpTest {

  private lateinit var server: RecordingSignatureServer
  private lateinit var session: NotebookKernelSession
  private lateinit var signatureHelp: NotebookSignatureHelp

  @Before
  fun setUp() {
    server = RecordingSignatureServer()
    val gateway: KernelGateway = FakeKernelGateway()
    session = NotebookKernelSession(gateway, BASE_SPACE) { "Analysis.ipynb" }
    signatureHelp =
      NotebookSignatureHelp(session, server, NotebookLanguageDocument(server, BASE_SPACE) { "Analysis.ipynb" })
  }

  /**
   * The entered Space's `pylsp` answers the signature help, never base's - `StdioLanguageServer`
   * runs one per Space.
   */
  @Test
  fun theAskGoesToTheDocumentsSpace() = runTest {
    val recording = SpaceRecordingLanguageServer(server)
    val asked =
      NotebookSignatureHelp(session, recording, NotebookLanguageDocument(recording, THESIS_SPACE) { "Analysis.ipynb" })
    session.open()

    asked.at(cells("cell-1"), "cell-1", "os.path.join(", 13)

    assertEquals(setOf(THESIS_SPACE.id), recording.spaceIds.toSet())
  }

  @Test
  fun noKernel_answersNothingAndAsksNobody() = runTest {
    assertEquals(null, signatureHelp.at(cells("cell-1"), "cell-1", "print(", 6))
    assertTrue("typing alone must not start the language server", server.syncs.isEmpty())
  }

  @Test
  fun withAKernel_theServersAnswerComesBack() = runTest {
    session.open()
    server.answer = HELP

    val answer = signatureHelp.at(cells("cell-1"), "cell-1", "os.path.join(", 13)

    assertEquals(HELP, answer)
  }

  @Test
  fun theCursor_asksAtItsVirtualPosition_pastTheCellsBeforeIt() = runTest {
    session.open()
    val cells =
      listOf(
        Cell("cell-1", CellKind.CODE, "import os"),
        Cell("cell-2", CellKind.CODE, "os.path.join("),
      )

    signatureHelp.at(cells, "cell-2", "os.path.join(", 13)

    // cell-1 is one line; two separators later cell-2's caret is virtual (3, 13).
    assertEquals(LanguagePosition(line = 3, character = 13), server.lastPosition)
  }

  /** The field's live text is what is synced, never `uiState`'s up-to-a-debounce-old copy. */
  @Test
  fun theAsk_syncsTheCellsLiveSource() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.CODE, "x = 1"))

    signatureHelp.at(cells, "cell-1", "print('a', ", 12)

    assertEquals("print('a', \n", server.syncs.single().text)
  }

  /** A markdown cell has no block in the Python document - `positionOf` answers null for it. */
  @Test
  fun aCellOutsideTheVirtualDocument_answersNothing() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.MARKDOWN, "# notes"))

    assertEquals(null, signatureHelp.at(cells, "cell-1", "# notes", 2))
    assertTrue(server.asks.isEmpty())
  }

  /** A `pylsp` that cannot answer is nothing to show, not a crash - hover's own rule. */
  @Test
  fun aServerThatCannotAnswer_isSilence() = runTest {
    session.open()
    server.failure = IllegalStateException("pylsp exited")

    assertEquals(null, signatureHelp.at(cells("cell-1"), "cell-1", "print(", 6))
  }

  @Test
  fun aCancelledAsk_staysCancelled() = runTest {
    session.open()
    server.failure = CancellationException("the ask was superseded")

    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking { signatureHelp.at(cells("cell-1"), "cell-1", "print(", 6) }
    }
  }

  private fun cells(vararg ids: String) = ids.map { Cell(it, CellKind.CODE, "x = 1") }

  /** Records the syncs and asks the adapter would hear, and can fail like a dead `pylsp`. */
  private class RecordingSignatureServer : LanguageServerPort by FakeLanguageServerPort() {
    data class Sync(val uri: String, val text: String)

    val syncs = mutableListOf<Sync>()
    val asks = mutableListOf<LanguagePosition>()
    var lastPosition: LanguagePosition? = null
      private set
    var failure: Exception? = null
    var answer: LanguageSignatureHelp? = null

    override suspend fun changeDocument(space: Space, uri: String, text: String) {
      syncs += Sync(uri, text)
    }

    override suspend fun signatureHelp(space: Space, uri: String, position: LanguagePosition): LanguageSignatureHelp? {
      lastPosition = position
      asks += position
      failure?.let { throw it }
      return answer
    }
  }

  private companion object {
    val HELP = LanguageSignatureHelp(
      signatures = listOf(
        LanguageSignature(
          label = "join(path: LiteralString, /, *paths: LiteralString) -> LiteralString",
          parameters = listOf(LanguageParameter("path"), LanguageParameter("paths")),
        ),
      ),
    )
  }
}
