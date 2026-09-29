package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerException
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageWorkspaceEdit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's rename ask - `textDocument/rename` at the caret's virtual
 * position, answered (by the shipped `pylsp`) as a `WorkspaceEdit.documentChanges` carrying
 * **one whole-document `TextEdit`** - the `def` line and every call site inside one `newText` -
 * and resliced into per-cell sources through the reconstruction guard.
 *
 * The session gate is the asks' shared rule (`NotebookAsks`'s own doc): a notebook that never
 * ran a cell never asked for a Space, so "Rename symbol" there must not boot one. What the
 * answer cannot be is reported rather than swallowed: a server refusal is a [RenameAnswer.Refused]
 * carrying its own message, and an answer that cannot be sliced back is one too - the half a
 * snackbar can say, where a dead socket is still silence.
 */
class NotebookRenameTest {

  private lateinit var server: RecordingRenameServer
  private lateinit var session: NotebookKernelSession
  private lateinit var rename: NotebookRename

  @Before
  fun setUp() {
    server = RecordingRenameServer()
    val gateway: KernelGateway = FakeKernelGateway()
    session = NotebookKernelSession(gateway, BASE_SPACE) { "Analysis.ipynb" }
    rename = NotebookRename(session, server, NotebookLanguageDocument(server) { "Analysis.ipynb" })
  }

  @Test
  fun noKernel_answersNothingAndAsksNobody() = runTest {
    assertNull(rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed"))
    assertTrue("a menu row alone must not start the language server", server.syncs.isEmpty())
    assertTrue(server.asks.isEmpty())
  }

  /** The caret sits in cell-2, two lines and two separators past cell-1 - virtual line 4. */
  @Test
  fun theAsk_asksForTheVirtualPositionUnderTheCaret() = runTest {
    session.open()

    rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed")

    assertEquals(LanguagePosition(4, 9), server.asks.single().position)
    assertEquals("summed", server.asks.single().newName)
  }

  /** The field's live text is what is synced, never `uiState`'s up-to-a-debounce-old copy. */
  @Test
  fun theAsk_syncsTheCellsLiveSource() = runTest {
    session.open()
    val stale = listOf(Cell("cell-1", CellKind.CODE, "x = 1"))

    rename.renamed(stale, "cell-1", "x = 1 typed", 4, "y")

    assertEquals("x = 1 typed\n", server.syncs.single())
  }

  /** The probe's own shape: one whole-document `TextEdit`, both cells' new sources inside it. */
  @Test
  fun aWholeDocumentRename_renamesEveryCellsSource() = runTest {
    session.open()
    server.answer = renamedDocument("def summed(items):\n    return sum(items)\n\n\nanswer = summed([1, 2])\n")

    val answer = rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed")

    assertEquals(
      RenameAnswer.Renamed(
        sources = mapOf(
          "cell-1" to "def summed(items):\n    return sum(items)",
          "cell-2" to "answer = summed([1, 2])",
        ),
        askedSources = mapOf(
          "cell-1" to "def total(items):\n    return sum(items)",
          "cell-2" to "answer = total([1, 2])",
          "cell-3" to "# notes",
        ),
      ),
      answer,
    )
  }

  /** A `WorkspaceEdit` for some other document is nothing this notebook can apply. */
  @Test
  fun anAnswerForAnotherDocument_isRefused() = runTest {
    session.open()
    server.answer = LanguageWorkspaceEdit(
      mapOf("file:///usr/lib/python3.12/posixpath.py" to listOf(edit(0, 0, 1, 0, "x\n"))),
    )

    assertEquals(
      RenameAnswer.Refused("No result."),
      rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed"),
    )
  }

  /** `{}` - the server found nothing to rename - is refused, not an empty rewrite. */
  @Test
  fun anEmptyWorkspaceEdit_isRefused() = runTest {
    session.open()
    server.answer = LanguageWorkspaceEdit(emptyMap())

    assertEquals(
      RenameAnswer.Refused("No result."),
      rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed"),
    )
  }

  /** An answer that rewrote a separator can never be sliced back - refused rather than guessed. */
  @Test
  fun anAnswerThatMovedASeparator_isRefused() = runTest {
    session.open()
    server.answer = renamedDocument("def summed(items):\n    return sum(items)\n\nanswer = summed([1, 2])\n")

    assertEquals(
      RenameAnswer.Refused("The rename could not be applied"),
      rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed"),
    )
  }

  /**
   * The server said no - jedi's own reply to a keyword or a name it cannot prove, which is the
   * one refusal with a message worth the snackbar: the server's own words, not a paraphrase.
   */
  @Test
  fun aServerError_isRefusedWithItsOwnMessage() = runTest {
    session.open()
    server.failure = LanguageServerException(
      method = "textDocument/rename",
      code = -32802,
      serverMessage = "there's no symbol that can be renamed",
    )

    assertEquals(
      RenameAnswer.Refused("there's no symbol that can be renamed"),
      rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed"),
    )
  }

  /** A `pylsp` that died is silence, not a crash and not a phoney refusal - the asks' own rule. */
  @Test
  fun aServerThatCannotAnswer_isSilence() = runTest {
    session.open()
    server.failure = IllegalStateException("pylsp exited")

    assertNull(rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed"))
  }

  @Test
  fun aCancelledAsk_staysCancelled() = runTest {
    session.open()
    server.failure = CancellationException("the ask was superseded")

    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking { rename.renamed(cells(), "cell-2", "answer = total([1, 2])", 9, "summed") }
    }
  }

  /** A markdown cell owns no block in the Python document - `positionOf` answers null for it. */
  @Test
  fun aCellOutsideTheVirtualDocument_answersNothing() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.MARKDOWN, "# notes"))

    assertNull(rename.renamed(cells, "cell-1", "# notes", 2, "x"))
    assertTrue(server.asks.isEmpty())
  }

  /**
   * cell-1's two-line `def` at virtual lines 0-1, cell-2's call site at line 4, and a markdown
   * cell owning no block - the probe's notebook, trimmed to what the ask reads.
   */
  private fun cells() = listOf(
    Cell("cell-1", CellKind.CODE, "def total(items):\n    return sum(items)"),
    Cell("cell-2", CellKind.CODE, "answer = total([1, 2])"),
    Cell("cell-3", CellKind.MARKDOWN, "# notes"),
  )

  /**
   * A `documentChanges` answer for the synced document - the probe's own `(0,0)` to
   * `(len(splitlines),0)` span, `(5,0)` for this fixture's five lines plus trailing newline.
   */
  private fun renamedDocument(newText: String) = LanguageWorkspaceEdit(mapOf(URI to listOf(edit(0, 0, 5, 0, newText))))

  private fun edit(startLine: Int, startChar: Int, endLine: Int, endChar: Int, newText: String) =
    LanguageEdit(LanguageRange(LanguagePosition(startLine, startChar), LanguagePosition(endLine, endChar)), newText)

  /** Records the syncs and asks the adapter would hear, and can fail like a dead or refusing `pylsp`. */
  private class RecordingRenameServer : LanguageServerPort by FakeLanguageServerPort() {
    data class Ask(val uri: String, val position: LanguagePosition, val newName: String)

    val syncs = mutableListOf<String>()
    val asks = mutableListOf<Ask>()
    var failure: Exception? = null
    var answer: LanguageWorkspaceEdit = LanguageWorkspaceEdit(emptyMap())

    override suspend fun changeDocument(space: Space, uri: String, text: String) {
      syncs += text
    }

    override suspend fun rename(
      space: Space,
      uri: String,
      position: LanguagePosition,
      newName: String,
    ): LanguageWorkspaceEdit {
      asks += Ask(uri, position, newName)
      failure?.let { throw it }
      return answer
    }
  }

  private companion object {
    /** The synced document's own uri - `NotebookVirtualDocument`'s, for "Analysis.ipynb". */
    val URI: String = NotebookVirtualDocument("Analysis.ipynb", emptyList()).uri
  }
}
