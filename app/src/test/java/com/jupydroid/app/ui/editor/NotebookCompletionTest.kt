package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageEdit
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
 * `docs/PLAN.md` P5c: the popup's source is the Space's language server first and the kernel
 * only when no server is running - behind the same [CompletionResult] the kernel path always
 * produced, so the popup and its splice do not change.
 *
 * The session gate is [NotebookKernel.completionsAt]'s own rule carried over: a notebook that
 * never ran a cell never asked for a Space, so typing there asks nothing of anyone.
 */
class NotebookCompletionTest {

  private val space = BASE_SPACE
  private lateinit var gateway: RecordingCompletionGateway
  private lateinit var server: RecordingLanguageServer
  private lateinit var session: NotebookKernelSession
  private lateinit var completion: NotebookCompletion

  @Before
  fun setUp() {
    gateway = RecordingCompletionGateway()
    server = RecordingLanguageServer()
    session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }
    completion = NotebookCompletion(session, gateway, server, NotebookLanguageDocument(server) { "Analysis.ipynb" })
  }

  @Test
  fun noKernel_answersNothingAndAsksNobody() = runTest {
    val result = completion.at(cells("cell-1"), "cell-1", "pri", 3)

    assertEquals(CompletionResult.NONE, result)
    assertTrue("the language server must not be started by typing alone", server.syncs.isEmpty())
    assertEquals(0, gateway.completionsAsked)
  }

  @Test
  fun withAKernel_theLanguageServerAnswersAndTheKernelIsNotAsked() = runTest {
    session.open()
    server.completions =
      listOf(
        LanguageCompletion(label = "print", insertText = "print"),
        LanguageCompletion(label = "property", insertText = "property"),
      )

    val result = completion.at(cells("cell-1"), "cell-1", "pri", 3)

    assertEquals(listOf("print", "property"), result.matches)
    assertEquals(0, gateway.completionsAsked)
  }

  @Test
  fun theDocument_isTheNotebooksCells_syncedWholeUnderOneUri() = runTest {
    session.open()
    val cells = listOf(Cell("cell-1", CellKind.CODE, "x = 1"), Cell("cell-2", CellKind.CODE, "y = 1"))

    completion.at(cells, "cell-1", "x = 12", 6)
    completion.at(cells, "cell-2", "y = 1", 5)

    val (first, second) = server.syncs
    assertEquals("one notebook is one document, however many cells ask", first.uri, second.uri)
    assertTrue("the uri must name the notebook: ${first.uri}", first.uri.endsWith("Analysis.ipynb.py"))
    assertTrue("pylsp wants a file scheme: ${first.uri}", first.uri.startsWith("file://"))
    // The first ask carried cell-1's live text (x = 12, not the saved x = 1) beside cell-2's.
    assertEquals("x = 12\n\n\ny = 1\n", first.text)
    assertEquals("the next ask re-syncs the stale version away", "x = 1\n\n\ny = 1\n", second.text)
  }

  @Test
  fun theCursor_asksAtItsVirtualPosition_pastTheCellsBeforeIt() = runTest {
    session.open()
    val cells =
      listOf(
        Cell("cell-1", CellKind.CODE, "import os"),
        Cell("cell-2", CellKind.CODE, "os.path.jo"),
      )

    completion.at(cells, "cell-2", "os.path.jo", 10)

    // cell-1 is one line; two separators later cell-2's offset 10 is virtual (3, 10).
    assertEquals(LanguagePosition(line = 3, character = 10), server.lastPosition)
  }

  @Test
  fun thePosition_countsLineAndCharacterInUtf16() = runTest {
    session.open()
    // "import os\n" is ten code units, so the cursor sits at line 1, character 10.
    val code = "import os\nos.path.jo"

    completion.at(cells("cell-1"), "cell-1", code, code.length)

    assertEquals(LanguagePosition(line = 1, character = 10), server.lastPosition)
  }

  @Test
  fun aTextEdit_splicesOverTheRangeItNames() = runTest {
    session.open()
    val code = "import os.path\nos.path.jo"
    server.completions =
      listOf(
        LanguageCompletion(
          label = "join",
          insertText = "join",
          edit = LanguageEdit(LanguageRange(LanguagePosition(1, 8), LanguagePosition(1, 10)), "join"),
        ),
      )

    val result = completion.at(cells("cell-1"), "cell-1", code, code.length)

    assertEquals(listOf("join"), result.matches)
    assertEquals(23, result.replacedStart)
    assertEquals(25, result.replacedEnd)
  }

  @Test
  fun aTextEdit_inVirtualCoordinates_mapsBackIntoTheCell() = runTest {
    session.open()
    // cell-1's one line plus two separators puts cell-2 at virtual line 3, so the edit's
    // (3, 8)-(3, 10) - which the server sends in virtual coordinates - is the cell's 8..10.
    val cells =
      listOf(
        Cell("cell-1", CellKind.CODE, "import os"),
        Cell("cell-2", CellKind.CODE, "os.path.jo"),
      )
    server.completions =
      listOf(
        LanguageCompletion(
          label = "join",
          insertText = "join",
          edit = LanguageEdit(LanguageRange(LanguagePosition(3, 8), LanguagePosition(3, 10)), "join"),
        ),
      )

    val result = completion.at(cells, "cell-2", "os.path.jo", 10)

    assertEquals(listOf("join"), result.matches)
    assertEquals(8, result.replacedStart)
    assertEquals(10, result.replacedEnd)
  }

  @Test
  fun aTextEdit_namingAnotherCell_fallsBackToTheIdentifier() = runTest {
    session.open()
    val cells =
      listOf(
        Cell("cell-1", CellKind.CODE, "import os"),
        Cell("cell-2", CellKind.CODE, "os.path.jo"),
      )
    // An edit whose range is outside the asked cell cannot be spliced into it - the popup
    // gets the identifier span instead, never a range that would delete another cell's text.
    server.completions =
      listOf(
        LanguageCompletion(
          label = "join",
          insertText = "join",
          edit = LanguageEdit(LanguageRange(LanguagePosition(0, 0), LanguagePosition(0, 6)), "join"),
        ),
      )

    val result = completion.at(cells, "cell-2", "os.path.jo", 10)

    assertEquals(8, result.replacedStart)
    assertEquals(10, result.replacedEnd)
  }

  @Test
  fun anItemWithoutAnEdit_replacesTheIdentifierBeforeTheCursor() = runTest {
    session.open()
    server.completions = listOf(LanguageCompletion(label = "arange", insertText = "arange"))

    val result = completion.at(cells("cell-1"), "cell-1", "np.ar", 5)

    assertEquals(3, result.replacedStart)
    assertEquals(5, result.replacedEnd)
  }

  @Test
  fun anItemWithoutAnEditAndNoIdentifier_insertsAtTheCursor() = runTest {
    session.open()
    server.completions = listOf(LanguageCompletion(label = "pi", insertText = "pi"))

    val result = completion.at(cells("cell-1"), "cell-1", "math.", 5)

    assertEquals(5, result.replacedStart)
    assertEquals(5, result.replacedEnd)
  }

  @Test
  fun twoItemsInsertingTheSameText_areOneRow() = runTest {
    session.open()
    server.completions =
      listOf(
        LanguageCompletion(label = "path", insertText = "path"),
        LanguageCompletion(
          label = "path",
          insertText = "path",
          edit = LanguageEdit(LanguageRange(LanguagePosition(0, 0), LanguagePosition(0, 2)), "path"),
        ),
      )

    val result = completion.at(cells("cell-1"), "cell-1", "pa", 2)

    assertEquals(listOf("path"), result.matches)
  }

  @Test
  fun anEmptyAnswer_isTheAnswer_notAReasonToAskTheKernel() = runTest {
    session.open()
    server.completions = emptyList()

    val result = completion.at(cells("cell-1"), "cell-1", "pri", 3)

    assertEquals(CompletionResult.NONE, result)
    assertEquals(0, gateway.completionsAsked)
  }

  @Test
  fun aServerThatCannotAnswer_leavesTheKernelItsTurn() = runTest {
    session.open()
    server.failure = IllegalStateException("pylsp exited")
    gateway.answer = CompletionResult(listOf("println"), 0, 3)

    val result = completion.at(cells("cell-1"), "cell-1", "pri", 3)

    assertEquals(listOf("println"), result.matches)
    assertEquals(1, gateway.completionsAsked)
  }

  @Test
  fun aKernelThatCannotAnswer_isSilence() = runTest {
    session.open()
    server.failure = IllegalStateException("pylsp exited")
    gateway.failure = IllegalStateException("socket closed")

    assertEquals(CompletionResult.NONE, completion.at(cells("cell-1"), "cell-1", "pri", 3))
  }

  @Test
  fun aCancelledAsk_staysCancelled() = runTest {
    session.open()
    server.failure = CancellationException("the next keystroke arrived")

    assertThrows(CancellationException::class.java) {
      kotlinx.coroutines.runBlocking { completion.at(cells("cell-1"), "cell-1", "pri", 3) }
    }
  }

  private fun cells(vararg ids: String) = ids.map { Cell(it, CellKind.CODE, "x = 1") }

  /** Counts the kernel's `complete` calls and can answer or refuse on demand. */
  private class RecordingCompletionGateway : KernelGateway by FakeKernelGateway() {
    var completionsAsked = 0
      private set
    var answer = CompletionResult.NONE
    var failure: Exception? = null

    override suspend fun complete(session: KernelSession, code: String, cursorOffset: Int): CompletionResult {
      completionsAsked++
      failure?.let { throw it }
      return answer
    }
  }

  /** Records the sync and the ask the adapter would hear, and can fail like a dead `pylsp`. */
  private class RecordingLanguageServer : LanguageServerPort by FakeLanguageServerPort() {
    data class Sync(val uri: String, val text: String)

    val syncs = mutableListOf<Sync>()
    var lastPosition: LanguagePosition? = null
      private set
    var failure: Exception? = null
    var completions: List<LanguageCompletion> = emptyList()

    override suspend fun changeDocument(space: com.jupydroid.app.domain.model.Space, uri: String, text: String) {
      syncs += Sync(uri, text)
    }

    override suspend fun completion(
      space: com.jupydroid.app.domain.model.Space,
      uri: String,
      position: LanguagePosition,
    ): List<LanguageCompletion> {
      lastPosition = position
      failure?.let { throw it }
      return completions
    }
  }
}
