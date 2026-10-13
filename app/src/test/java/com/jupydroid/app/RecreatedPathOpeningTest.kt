package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.model.BASE_SPACE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A path an open tab was renamed away from belongs to whatever is created there next.
 *
 * jupyterlab@7f0e6906 gives every main-area widget an id of its own
 * (`packages/apputils/src/mainareawidget.ts`, `DOMUtils.createDomID`) and `openOrReveal` matches a
 * context by the path it lives at **now** (`packages/docmanager/src/manager.ts`, `_contextsForPath`),
 * so a renamed widget never answers to its old name. A key that *was* its path did: the renamed
 * tab stayed `Editor("a.ipynb")`, and every open of a recreated `a.ipynb` revealed it instead.
 */
class RecreatedPathOpeningTest {
  private val explorer = Explorer(BASE_SPACE.id)

  @Test
  fun aNotebookRecreatedAtARenamedTabsOldNameOpensATabOfItsOwn() {
    val open = OpenDocuments()
    val backStack = mutableListOf<NavKey>(explorer, Editor("a.ipynb"))
    open.opened(movedTo("b.ipynb", DocumentOpening("a.ipynb")))

    backStack.openDocument("a.ipynb", open)

    assertEquals(listOf(explorer, Editor("a.ipynb"), Editor("a.ipynb", opening = 1)), backStack)
  }

  @Test
  fun aFileRecreatedAtARenamedTabsOldNameOpensATabOfItsOwn() {
    val open = OpenDocuments()
    val backStack = mutableListOf<NavKey>(explorer, FileEditor("a.py"))
    open.opened(movedTo("b.py", DocumentOpening("a.py")))

    backStack.openDocument("a.py", open)

    assertEquals(listOf(explorer, FileEditor("a.py"), FileEditor("a.py", opening = 1)), backStack)
  }

  /** Both names reach their own tab, and neither open stacks a third. */
  @Test
  fun theRenamedTabAndTheRecreatedOneAreEachRevealedByTheirOwnName() {
    val open = OpenDocuments()
    open.opened(movedTo("b.ipynb", DocumentOpening("a.ipynb")))
    open.opened(MovedOpenDocument(DocumentOpening("a.ipynb", 1)))
    val backStack = mutableListOf<NavKey>(explorer, Editor("a.ipynb"), Editor("a.ipynb", opening = 1))

    backStack.openDocument("b.ipynb", open)
    assertEquals(listOf(explorer, Editor("a.ipynb", opening = 1), Editor("a.ipynb")), backStack)

    backStack.openDocument("a.ipynb", open)
    assertEquals(listOf(explorer, Editor("a.ipynb"), Editor("a.ipynb", opening = 1)), backStack)
  }

  /** Two documents opened under one path: each tab's lookup lands on its own, not on the first. */
  @Test
  fun eachTabFindsItsOwnDocumentWhenTwoOpenedUnderOnePath() {
    val open = OpenDocuments()
    val renamed = movedTo("b.ipynb", DocumentOpening("a.ipynb"))
    val recreated = MovedOpenDocument(DocumentOpening("a.ipynb", 1))
    open.opened(renamed)
    open.opened(recreated)

    assertSame(renamed, Editor("a.ipynb").documentIn(open))
    assertSame(recreated, Editor("a.ipynb", opening = 1).documentIn(open))
  }

  /**
   * A tab beneath the top may not have registered its document yet - it registers when it first
   * composes - and with nothing to say it moved, it still lives where it opened.
   */
  @Test
  fun aTabWhoseDocumentHasNotRegisteredIsRevealedByThePathItOpenedUnder() {
    val backStack = mutableListOf<NavKey>(explorer, Editor("a.ipynb"), Editor("c.ipynb"))

    backStack.openDocument("a.ipynb", OpenDocuments())

    assertEquals(listOf(explorer, Editor("c.ipynb"), Editor("a.ipynb")), backStack)
  }

  /** The opening a new tab takes is the lowest no open tab on that path holds, so keys stay small. */
  @Test
  fun aNewTabTakesTheLowestOpeningNoOpenTabOnItsPathHolds() {
    val open = OpenDocuments()
    val backStack = mutableListOf<NavKey>(explorer, Editor("a.ipynb", opening = 1))
    open.opened(movedTo("b.ipynb", DocumentOpening("a.ipynb", 1)))

    backStack.openDocument("a.ipynb", open)

    assertEquals(listOf(explorer, Editor("a.ipynb", opening = 1), Editor("a.ipynb")), backStack)
  }

  private fun movedTo(path: String, openedAs: DocumentOpening) = MovedOpenDocument(openedAs).also { it.path = path }
}
