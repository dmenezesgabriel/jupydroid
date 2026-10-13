package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.model.BASE_SPACE
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reveal-don't-reopen rule, on its own, where the cases are cheap to state.
 *
 * The device half is `NotebookNavigationTest.tappingTheSameNotebookTwiceOpensOneEditor`; this is
 * the half that says what "already open" means when it is not the notebook on top.
 *
 * The stack holds **every** open document, the way JupyterLab's main area holds every
 * open tab: opening a second document pushes it, and opening one already in the stack moves it to
 * the top rather than duplicating it - two equal keys would share one `contentKey`, one
 * `ViewModelStore` and one `SaveableStateHolder` slot.
 */
class NotebookBackStackTest {
  @Test
  fun openingANotebookPushesItsEditor() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1")), backStack)
  }

  @Test
  fun openingTheNotebookAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1")), backStack)
  }

  /**
   * A second notebook **stacks**: both stay open, the way `shell.add(widget, 'main')` adds a tab
   * rather than closing the current one. Keeping `Editor(a)`'s entry is also what keeps its
   * ViewModel - and with it the kernel session - alive underneath.
   */
  @Test
  fun openingADifferentNotebookKeepsTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Editor("nb-2")), backStack)
  }

  /**
   * Reopening a document already in the stack **promotes** it to the top rather than duplicating
   * it - the activate-half of `openOrReveal` (`packages/docmanager/src/manager.ts:481-503`), and
   * the one thing a stack of tabs still cannot do: hold two equal keys.
   */
  @Test
  fun reopeningAnOpenNotebookPromotesItToTheTop() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Editor("nb-2"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-2"), Editor("nb-1")), backStack)
  }

  /**
   * `openOrReveal` matches a widget by its context's **current** path - `manager.ts:481-503`
   * reaches the match through `_contextsForPath`, which filters on the path a context lives at
   * *now*. A `NavKey` cannot ask that: it keeps only the path it was opened under (its
   * `contentKey` is what keeps the entry's `ViewModelStore`), so a document renamed since it
   * opened answers to a path the stack has never seen. Opening by live path asks [OpenDocuments]
   * for the `openedAs` the stack still knows - a second `Editor` entry for the same file would
   * be a second ViewModel and a second autosave on one document.
   */
  @Test
  fun openingARenamedDocumentRevealsItInsteadOfOpeningItAgain() {
    val open = OpenDocuments()
    val document = MovedOpenDocument(DocumentOpening("a.ipynb"))
    open.opened(document)
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("a.ipynb"))

    // The `changes` adoption: the file was renamed beside its open document, which re-keyed.
    document.path = "b.ipynb"
    backStack.openDocument("b.ipynb", open)

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("a.ipynb")), backStack)
  }

  /**
   * A path nothing is open on falls through the reveal and opens by its own suffix, exactly as
   * [openNotebook]/[openFile] always did.
   */
  @Test
  fun openingAPathNothingIsOpenOnPushesByItsSuffix() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("a.ipynb"))

    backStack.openDocument("b.ipynb", OpenDocuments())
    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("a.ipynb"), Editor("b.ipynb")), backStack)

    backStack.openDocument("c.py", OpenDocuments())
    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("a.ipynb"), Editor("b.ipynb"), FileEditor("c.py")), backStack)
  }

  /** A plain file opens the same way a notebook does: pushed onto whatever is open. */
  @Test
  fun openingAFilePushesItsEditor() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id))

    backStack.openFile("data.csv")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), FileEditor("data.csv")), backStack)
  }

  @Test
  fun openingTheFileAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), FileEditor("data.csv"))

    backStack.openFile("data.csv")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), FileEditor("data.csv")), backStack)
  }

  @Test
  fun openingADifferentFileStacksOnTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), FileEditor("a.csv"))

    backStack.openFile("b.csv")

    assertEquals(
      listOf(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), FileEditor("a.csv"), FileEditor("b.csv")),
      backStack,
    )
  }

  /**
   * The two document kinds stack on each other - a notebook and a plain file are both main-area
   * tabs, and neither closes the other.
   */
  @Test
  fun openingANotebookStacksOnAPlainFileAndViceVersa() {
    val fromFile = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), FileEditor("a.py"))
    fromFile.openNotebook("nb-1")
    assertEquals(listOf(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), FileEditor("a.py"), Editor("nb-1")), fromFile)

    val fromNotebook = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), Editor("nb-1"))
    fromNotebook.openFile("a.py")
    assertEquals(
      listOf(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), Editor("nb-1"), FileEditor("a.py")),
      fromNotebook,
    )
  }

  /** A terminal opens the same way: a document of its own kind, pushed onto the browser. */
  @Test
  fun openingATerminalPushesItsScreen() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openTerminal("1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Terminal("1")), backStack)
  }

  /**
   * A second "new terminal" tap always opens a fresh session - two terminals never compare equal
   * by name, since the server mints a new one every time - so it stacks, the way the reference's
   * `terminal:create-new` adds a second tab rather than revealing the first.
   */
  @Test
  fun openingASecondTerminalStacksOnTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Terminal("1"))

    backStack.openTerminal("2")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Terminal("1"), Terminal("2")), backStack)
  }

  /** A terminal stacks on a document exactly as the other two kinds stack on each other. */
  @Test
  fun openingATerminalStacksOnAnEditorAndViceVersa() {
    val fromEditor = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), Editor("nb-1"))
    fromEditor.openTerminal("1")
    assertEquals(listOf(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), Editor("nb-1"), Terminal("1")), fromEditor)

    val fromTerminal = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), Terminal("1"))
    fromTerminal.openNotebook("nb-1")
    assertEquals(listOf(Explorer(BASE_SPACE.id), Explorer(BASE_SPACE.id), Terminal("1"), Editor("nb-1")), fromTerminal)
  }

  /**
   * The same notebook, already open **beneath** whatever is on top, is promoted rather than
   * duplicated. `lastOrNull() == document` only ever checked the top, so a notebook one level
   * deeper survived a swap of what sat above it and came back a second time on the next
   * [openNotebook] for the same id. Two entries would then
   * share one `contentKey` ([openNotebook]'s own doc comment), and popping either would clear
   * `ViewModelStore`/`SaveableStateHolder` state the other was still relying on.
   */
  @Test
  fun reopeningANotebookStackedBeneathSomethingElsePromotesItInstead() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Terminal("1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Terminal("1"), Editor("nb-1")), backStack)
  }

  /**
   * A Console stacks on top of the notebook it shares a kernel with, and that is its reason to
   * exist: Back has to return to the still-open notebook, not to the browser. [openConsole] is
   * not [openNotebook]'s twin for that reason.
   */
  @Test
  fun openingAConsolePushesItOnTopOfTheOpenNotebook() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"))

    backStack.openConsole("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1")), backStack)
  }

  @Test
  fun openingTheConsoleAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1"))

    backStack.openConsole("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1")), backStack)
  }

  /**
   * A document opened while a Console shows leaves the console's whole stack beneath it: the
   * console is a main-area widget like any other now, and so is the notebook it was attached to.
   */
  @Test
  fun openingADifferentNotebookWhileAConsoleIsOpenLeavesEverythingBeneath() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1"), Editor("nb-2")), backStack)
  }

  /**
   * Reopening the notebook a Console is stacked over promotes the notebook **past** the console -
   * reveal moves it to the top, and the console stays open beneath, the way the reference keeps
   * an attached console's tab when its notebook's is activated.
   */
  @Test
  fun reopeningTheNotebookUnderAnOpenConsolePromotesItPastTheConsole() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Console("nb-1"), Editor("nb-1")), backStack)
  }

  /** Settings, opened with nothing else showing, pushes onto the bare browser. */
  @Test
  fun openingSettingsPushesItsScreen() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openSettings()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Settings), backStack)
  }

  @Test
  fun openingSettingsAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Settings)

    backStack.openSettings()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Settings), backStack)
  }

  /**
   * Settings stacks on top of an open notebook rather than replacing it - [openConsole]'s own
   * reason: visiting it should not dispose the document underneath, so Back has to return there.
   */
  @Test
  fun openingSettingsStacksOnTopOfAnOpenNotebook() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"))

    backStack.openSettings()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Settings), backStack)
  }

  /**
   * A document opened while Settings shows leaves both beneath - Settings is a main-area widget
   * too, and the document it sat on is still an open tab.
   */
  @Test
  fun openingANotebookWhileSettingsIsOpenLeavesBothBeneath() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Settings)

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Settings, Editor("nb-2")), backStack)
  }

  /** The Launcher is a detail-pane destination: it pushes onto the bare browser. */
  @Test
  fun openingTheLauncherPushesIt() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openLauncher()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Launcher), backStack)
  }

  @Test
  fun openingTheLauncherAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Launcher)

    backStack.openLauncher()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Launcher), backStack)
  }

  /**
   * Reaching the launcher stacks it on top of an open document rather than replacing it -
   * `openSettings`'s own shape, since visiting it must not dispose the document underneath. The
   * reference adds it as a main-area *tab* beside the open ones (`launcher:create`).
   */
  @Test
  fun openingTheLauncherStacksOnTopOfAnOpenDocument() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"))

    backStack.openLauncher()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Launcher), backStack)
  }

  /**
   * At most one Launcher ever exists (the reference's own rule, `maybeCreate`'s). A `data object`
   * key shares `contentKey` across equal entries, so a second `Launcher` beneath anything stacked
   * over it - `[Explorer, Launcher, Settings]` - must come forward rather than be duplicated, the
   * same invariant documents have.
   */
  @Test
  fun openingTheLauncherWithOneDeeperInTheStackPromotesIt() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Launcher, Settings)

    backStack.openLauncher()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Settings, Launcher), backStack)
  }

  /**
   * The Launcher is a tab like any other main-area widget: opening a document **from the browser**
   * does not close it - `launcher.dispose()` runs only when the launcher itself spawned the
   * widget (`add(item, 'main', {ref: id})`, `launcher-extension/src/index.ts:95-106`), which is
   * the launch handler's rule, not the opener's.
   */
  @Test
  fun openingANotebookFromTheListLeavesTheLauncherOpen() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Launcher)

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Launcher, Editor("nb-1")), backStack)
  }

  /** The launcher stays open even with documents stacked between. */
  @Test
  fun openingAFileLeavesALauncherStackedOverTheOpenDocument() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Launcher)

    backStack.openFile("a.py")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Launcher, FileEditor("a.py")), backStack)
  }

  /**
   * `shell.isEmpty('main')` - the question `maybeCreate` asks. A lone browser is an empty main
   * area; a Launcher, a document, or anything stacked in the detail slot is not.
   */
  @Test
  fun theMainAreaIsEmptyOnlyWhenNoDetailEntryExists() {
    assertEquals(true, listOf<NavKey>(Explorer(BASE_SPACE.id)).mainAreaIsEmpty())
    assertEquals(false, listOf<NavKey>(Explorer(BASE_SPACE.id), Launcher).mainAreaIsEmpty())
    assertEquals(false, listOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1")).mainAreaIsEmpty())
    assertEquals(false, listOf<NavKey>(Explorer(BASE_SPACE.id), Launcher, Settings).mainAreaIsEmpty())
  }

  /**
   * `main.title.closable = Array.from(labShell.widgets('main')).length > 1`
   * (`launcher-extension/src/index.ts:126`) - a launcher that is the *only* thing in the main area
   * gets no close affordance, because there is nothing beneath it to return to. The count is of
   * main widgets, so a document or the Settings underneath it is what makes it closable.
   */
  @Test
  fun aLauncherIsClosableOnlyWhileAnotherMainWidgetExists() {
    assertEquals(false, listOf<NavKey>(Explorer(BASE_SPACE.id)).launcherIsClosable())
    assertEquals(false, listOf<NavKey>(Explorer(BASE_SPACE.id), Launcher).launcherIsClosable())
    assertEquals(true, listOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Launcher).launcherIsClosable())
    assertEquals(true, listOf<NavKey>(Explorer(BASE_SPACE.id), Launcher, Settings).launcherIsClosable())
  }

  /**
   * A Launcher-made Console is a document in its own right - `shell.add(panel, 'main')`
   * (`console-extension/src/index.ts`), not [openConsole]'s stack-on-top - and the launcher that
   * minted it is disposed by the launch handler, not by this opener.
   */
  @Test
  fun aStandaloneConsoleStacksOnTheDetail() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Launcher)

    backStack.openStandaloneConsole("console-1-x", "Console 1")

    assertEquals(
      listOf(Explorer(BASE_SPACE.id), Launcher, Console("console-1-x", "Console 1")),
      backStack,
    )
  }

  /**
   * One console at a time. `entry<Console>` reads the *single* `consoleSession` holder, so a
   * second Console entry anywhere in the stack would draw its screen against the newer kernel -
   * the sweep is in both openers for that reason, not only the standalone's.
   */
  @Test
  fun aStandaloneConsoleSweepsAnAttachedOne() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1"))

    backStack.openStandaloneConsole("console-1-x", "Console 1")

    assertEquals(
      listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("console-1-x", "Console 1")),
      backStack,
    )
  }

  @Test
  fun aSecondStandaloneConsoleReplacesTheFirst() {
    val backStack =
      mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Console("console-1-x", "Console 1"))

    backStack.openStandaloneConsole("console-2-y", "Console 2")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Console("console-2-y", "Console 2")), backStack)
  }

  @Test
  fun openingAnAttachedConsoleSweepsAStandaloneOneFromTheStack() {
    val backStack = mutableListOf<NavKey>(
      Explorer(BASE_SPACE.id),
      Editor("nb-1"),
      Console("console-1-x", "Console 1"),
    )

    backStack.openConsole("nb-1")

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Console("nb-1")), backStack)
  }

  /**
   * `labShell.widgets('main')` - the strip's whole model: every main-area entry in stack order.
   * The browser is the list pane, never a tab, so it is the one kind the projection drops.
   */
  @Test
  fun theTabListIsEveryMainAreaEntryInStackOrder() {
    val backStack = mutableListOf<NavKey>(
      Explorer(BASE_SPACE.id),
      Editor("nb-1"),
      FileEditor("a.py"),
      Terminal("1"),
      Console("nb-1"),
      Settings,
      Launcher,
    )

    assertEquals(
      listOf(Editor("nb-1"), FileEditor("a.py"), Terminal("1"), Console("nb-1"), Settings, Launcher),
      backStack.mainAreaTabs(),
    )
  }

  /**
   * A tab's press is `shell.activateById` - the entry moves to the top, which is the same
   * remove-then-add [openInMainArea] already does for a reveal. Nothing else in the stack moves.
   */
  @Test
  fun activatingATabBringsItsEntryToTheTop() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), FileEditor("a.py"), Terminal("1"))

    backStack.activateMainAreaTab(Editor("nb-1"))

    assertEquals(listOf(Explorer(BASE_SPACE.id), FileEditor("a.py"), Terminal("1"), Editor("nb-1")), backStack)
  }

  @Test
  fun activatingTheTabAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("nb-1"), Terminal("1"))

    backStack.activateMainAreaTab(Terminal("1"))

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("nb-1"), Terminal("1")), backStack)
  }

  /**
   * The order the strip draws is **opening order**, not stack order. `activateById` changes which
   * widget is current and nothing else - `DockPanel._onTabActivateRequested` swaps `currentWidget`
   * without touching the tab bar's model (`packages/application/src/shell.ts`), so a click must not
   * move the tab it pressed. The stack still promotes the entry to its top, because the top is what
   * the scene draws; the strip's projection is where activation stays invisible.
   */
  @Test
  fun activatingATabDoesNotMoveItInTheStrip() {
    var order = emptyList<NavKey>()
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("a.ipynb"), FileEditor("b.py"), Terminal("1"))
    order = backStack.mainAreaTabs().orderedAsOpened(order)

    backStack.activateMainAreaTab(Editor("a.ipynb"))
    order = backStack.mainAreaTabs().orderedAsOpened(order)

    assertEquals(Editor("a.ipynb"), backStack.last())
    assertEquals(listOf(Editor("a.ipynb"), FileEditor("b.py"), Terminal("1")), order)
  }

  /**
   * Revealing an open document promotes the stack entry but leaves its tab where it was opened -
   * `openOrReveal` activates the widget that already exists (`manager.ts:481-503`), which is the
   * same no-reorder the tab's own press gets. New tabs join at the end and closed ones leave.
   */
  @Test
  fun revealingKeepsItsPlaceOpeningAppendsAndClosingDrops() {
    var order = emptyList<NavKey>()
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("a.ipynb"), FileEditor("b.py"))
    order = backStack.mainAreaTabs().orderedAsOpened(order)

    backStack.openNotebook("a.ipynb")
    order = backStack.mainAreaTabs().orderedAsOpened(order)
    assertEquals(listOf(Editor("a.ipynb"), FileEditor("b.py")), order)

    backStack.openTerminal("1")
    order = backStack.mainAreaTabs().orderedAsOpened(order)
    assertEquals(listOf(Editor("a.ipynb"), FileEditor("b.py"), Terminal("1")), order)

    backStack.remove(FileEditor("b.py"))
    order = backStack.mainAreaTabs().orderedAsOpened(order)
    assertEquals(listOf(Editor("a.ipynb"), Terminal("1")), order)
  }

  /**
   * Closing the **current** tab activates a surviving sibling - Lumino moves `currentIndex` to the
   * next tab, or the previous one when the closed was last (`TabBar._onCloseRequest` re-selection).
   * The browser is not a tab, so a stack that would land on it moves the sibling document up
   * instead; closing a background tab changes nothing above it.
   */
  @Test
  fun closingTheCurrentTabRevealsItsNeighbourNotTheBrowser() {
    val backStack =
      mutableListOf<NavKey>(Explorer(BASE_SPACE.id), Editor("a.ipynb"), Editor("b.ipynb"), FileEditor("c.py"))
    val order = listOf(Editor("a.ipynb"), Editor("b.ipynb"), FileEditor("c.py"))

    backStack.closeMainAreaTab(FileEditor("c.py"), order)
    assertEquals(Editor("b.ipynb"), backStack.last())

    backStack.closeMainAreaTab(Editor("a.ipynb"), order)
    assertEquals("a background tab closes quietly", Editor("b.ipynb"), backStack.last())

    backStack.closeMainAreaTab(Editor("b.ipynb"), order)
    assertEquals("the last tab leaves the browser", Explorer(BASE_SPACE.id), backStack.last())
  }

  /**
   * `title.label` - for a document it is `PathExt.basename(localPath)` (`docregistry/src/default.ts`),
   * and a renamed notebook answers to its **live** path while the key keeps the one it opened under.
   * The other kinds carry their own labels: the terminal's name, the console's `name || 'Console %1'`,
   * the launcher's and settings' own nouns.
   */
  @Test
  fun aTabTitleIsTheDocumentsBasenameOrTheKindsOwnLabel() {
    val moved = mapOf<NavKey, String>(Editor("old.ipynb") to "new.ipynb")

    assertEquals("nb-1.ipynb", Editor("nb-1.ipynb").mainAreaTabTitle(emptyMap()))
    assertEquals("new.ipynb", Editor("old.ipynb").mainAreaTabTitle(moved))
    assertEquals("a.py", FileEditor("dir/a.py").mainAreaTabTitle(emptyMap()))
    assertEquals("Terminal 1", Terminal("1").mainAreaTabTitle(emptyMap()))
    assertEquals("Console 2", Console("p", "Console 2").mainAreaTabTitle(emptyMap()))
    assertEquals("Launcher", Launcher.mainAreaTabTitle(emptyMap()))
    assertEquals("Settings", Settings.mainAreaTabTitle(emptyMap()))
  }

  /**
   * The reconcile the shell runs per pass: the strip's order reseeds, and the two per-key maps
   * drop keys whose tabs left. A dead live-path entry is the wrong answer a reopened name would
   * otherwise get - the composition-level half is OpenDocumentMarkingTest.
   */
  @Test
  fun theReconcileDropsAClosedTabsKeys() {
    val order = mutableListOf<NavKey>(Editor("a.ipynb"), Explorer(BASE_SPACE.id), Terminal("1"))
    val titles = mutableMapOf<NavKey, String>(Terminal("1") to "bash", Editor("a.ipynb") to "b.ipynb")
    val paths = mutableMapOf<NavKey, String>(Editor("a.ipynb") to "b.ipynb")

    reconcileOpenTabs(listOf(Explorer(BASE_SPACE.id), Terminal("1")), order, titles, paths)

    assertEquals(listOf(Explorer(BASE_SPACE.id), Terminal("1")), order)
    assertEquals(mapOf(Terminal("1") to "bash"), titles)
    assertEquals(emptyMap<NavKey, String>(), paths)
  }
}
