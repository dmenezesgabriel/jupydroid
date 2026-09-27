package com.jupydroid.app

import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Where a document that is **open** is reached by the path it lives at.
 *
 * `ITextModelService` seen from the one caller that needs it: `BulkTextEdits` resolves a model for
 * every resource it is about to edit, open or not (`bulkTextEdits.ts:227`), so a replace edits the
 * model a screen is showing rather than writing the file behind its back.
 */
class OpenDocumentsTest {
  /** A document that is only what the registry asks of one - see the interface it implements. */
  private class FakeOpenDocument(override var path: String, private var text: String) : OpenDocument {
    var applied: List<TextEdit> = emptyList()
      private set

    /** Nothing renames a fake: what it opened under is where it still lives. */
    override val openedAs: String = path

    // The strip's half of the contract - never asked of a document in this suite.
    override val dirty = MutableStateFlow(false)
    override fun saveNow(onSaved: () -> Unit) = onSaved()
    override fun discardChanges() = Unit

    override fun sourceOf(cell: Int?): String? = text.takeIf { cell == null }

    override fun applyEdits(cell: Int?, edits: List<TextEdit>) {
      applied = applied + edits
    }
  }

  @Test
  fun nothingIsOpenUntilADocumentSaysSo() {
    assertNull(OpenDocuments().at("a.py"))
  }

  @Test
  fun aDocumentThatOpenedIsReachedByItsPath() {
    val open = OpenDocuments()
    val document = FakeOpenDocument("reports/a.py", "cat")

    open.opened(document)

    assertSame(document, open.at("reports/a.py"))
  }

  /** A path nothing is open on is not this registry's problem - the engine reads the file instead. */
  @Test
  fun aPathNothingIsOpenOnAnswersNothing() {
    val open = OpenDocuments()
    open.opened(FakeOpenDocument("reports/a.py", "cat"))

    assertNull(open.at("reports/b.py"))
  }

  /**
   * **A rename moves the document, and the registry follows it** - the path is read live rather
   * than copied in. `Context._updatePath` reassigns in place while the widget carries on
   * (`docregistry/src/context.ts`), and `_contextsForPath` filters by a context's *current* path.
   */
  @Test
  fun aDocumentThatWasRenamedIsReachedByWhereItLivesNow() {
    val open = OpenDocuments()
    val document = FakeOpenDocument("a.py", "cat")
    open.opened(document)

    document.path = "reports/a.py"

    assertNull(open.at("a.py"))
    assertSame(document, open.at("reports/a.py"))
  }

  @Test
  fun aDocumentThatClosedIsNotOpenAnyMore() {
    val open = OpenDocuments()
    val document = FakeOpenDocument("a.py", "cat")
    open.opened(document)

    open.closed(document)

    assertNull(open.at("a.py"))
  }

  /**
   * **The swap race, and it is not hypothetical.** Reordering an entry to the top can run the old
   * ViewModel's `onCleared` *after* another document has registered, so closing by identity is
   * what keeps that from clearing the document that is actually on screen.
   */
  @Test
  fun aDocumentClosingAfterAnotherOpenedLeavesTheOpenOneAlone() {
    val open = OpenDocuments()
    val outgoing = FakeOpenDocument("a.py", "cat")
    val incoming = FakeOpenDocument("b.py", "cat")
    open.opened(outgoing)
    open.opened(incoming)

    open.closed(outgoing)

    assertNull(open.at("a.py"))
    assertSame(incoming, open.at("b.py"))
  }

  /**
   * Many at once, because the back stack holds every open document (P8d): the registry answers
   * each by its own live path, and reports them in the order they opened - the order P8e's tab
   * strip draws.
   */
  @Test
  fun openDocumentsAreAllReachableInTheOrderTheyOpened() {
    val open = OpenDocuments()
    val first = FakeOpenDocument("a.py", "cat")
    val second = FakeOpenDocument("b.py", "cat")
    open.opened(first)
    open.opened(second)

    assertSame(first, open.at("a.py"))
    assertSame(second, open.at("b.py"))
    assertEquals(listOf(first, second), open.all)
  }

  /** Registering the same document twice is not a second document - identity, not path. */
  @Test
  fun aDocumentRegisteredTwiceIsStillOneEntry() {
    val open = OpenDocuments()
    val document = FakeOpenDocument("a.py", "cat")
    open.opened(document)
    open.opened(document)

    assertEquals(listOf(document), open.all)
  }

  /** The registry hands the document over; what an edit *means* is the document's own business. */
  @Test
  fun anEditReachesTheDocumentThatIsOpen() {
    val open = OpenDocuments()
    val document = FakeOpenDocument("a.py", "cat")
    open.opened(document)

    open.at("a.py")?.applyEdits(cell = null, edits = listOf(TextEdit(0..2, "dog")))

    assertEquals(listOf(TextEdit(0..2, "dog")), document.applied)
  }
}
