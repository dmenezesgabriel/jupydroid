package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * `docs/PLAN.md` P5d-2b: the notebook's **one** document under the language server - every
 * code cell joined whole, synced on every ask and every connected edit, and kept as the mirror
 * (`synced`) that `publishDiagnostics` ranges map back through. Syncing this twice would make
 * the server see two documents; sharing one mirror is what makes a diagnostic drawn from the
 * server's own words land on the right cell.
 */
class NotebookLanguageDocumentTest {

  private lateinit var server: RecordingSyncServer
  private lateinit var document: NotebookLanguageDocument

  @Before
  fun setUp() {
    server = RecordingSyncServer()
    document = NotebookLanguageDocument(server) { "Analysis.ipynb" }
  }

  @Test
  fun sync_sendsTheWholeVirtualTextUnderTheNotebookUri() = runTest {
    val cells = listOf(code("c1", "import os"), code("c2", "os.getcwd()"))

    val synced = document.sync(cells)

    val sync = server.syncs.single()
    assertEquals(synced.uri, sync.uri)
    assertEquals("file:///jupyterdroid/notebooks/Analysis.ipynb.py", sync.uri)
    // The P5d-1 layout: block text + trailing newline, two blank lines between blocks.
    assertEquals("import os\n\n\nos.getcwd()\n", sync.text)
  }

  @Test
  fun aSync_publishesTheMirrorTheDiagnosticsWillMapAgainst() = runTest {
    val synced = document.sync(listOf(code("c1", "x = 1")))

    assertSame(synced, document.synced.value)
  }

  @Test
  fun aSecondSync_replacesTheMirrorSoRangesAlwaysMapAgainstWhatTheServerLastSaw() = runTest {
    document.sync(listOf(code("c1", "x = 1\ny = 2")))
    val second = document.sync(listOf(code("c1", "x = 1")))

    assertSame(second, document.synced.value)
    assertEquals("x = 1\n", server.syncs.last().text)
  }

  /** The syncs the adapter would hear, recorded for the tests to assert on. */
  private class RecordingSyncServer(private val fake: FakeLanguageServerPort = FakeLanguageServerPort()) :
    LanguageServerPort by fake {
    data class Sync(val uri: String, val text: String)

    val syncs = mutableListOf<Sync>()

    override suspend fun changeDocument(space: Space, uri: String, text: String) {
      syncs += Sync(uri, text)
    }
  }

  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)
}
