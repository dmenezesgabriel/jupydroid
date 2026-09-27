package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reveal-don't-reopen rule, on its own, where the cases are cheap to state.
 *
 * The device half is `NotebookNavigationTest.tappingTheSameNotebookTwiceOpensOneEditor`; this is
 * the half that says what "already open" means when it is not the notebook on top.
 *
 * Since P8d the stack holds **every** open document, the way JupyterLab's main area holds every
 * open tab: opening a second document pushes it, and opening one already in the stack moves it to
 * the top rather than duplicating it - two equal keys would share one `contentKey`, one
 * `ViewModelStore` and one `SaveableStateHolder` slot (P6b-0).
 */
class NotebookBackStackTest {
  @Test
  fun openingANotebookPushesItsEditor() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
  }

  @Test
  fun openingTheNotebookAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
  }

  /**
   * A second notebook **stacks**: both stay open, the way `shell.add(widget, 'main')` adds a tab
   * rather than closing the current one. Keeping `Editor(a)`'s entry is also what keeps its
   * ViewModel - and with it the kernel session - alive underneath.
   */
  @Test
  fun openingADifferentNotebookKeepsTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-1"), Editor("nb-2")), backStack)
  }

  /**
   * Reopening a document already in the stack **promotes** it to the top rather than duplicating
   * it - the activate-half of `openOrReveal` (`packages/docmanager/src/manager.ts:481-503`), and
   * the one thing a stack of tabs still cannot do: hold two equal keys.
   */
  @Test
  fun reopeningAnOpenNotebookPromotesItToTheTop() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Editor("nb-2"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-2"), Editor("nb-1")), backStack)
  }

  /** A plain file opens the same way a notebook does: pushed onto whatever is open. */
  @Test
  fun openingAFilePushesItsEditor() {
    val backStack = mutableListOf<NavKey>(Explorer, Explorer)

    backStack.openFile("data.csv")

    assertEquals(listOf(Explorer, Explorer, FileEditor("data.csv")), backStack)
  }

  @Test
  fun openingTheFileAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("data.csv"))

    backStack.openFile("data.csv")

    assertEquals(listOf(Explorer, Explorer, FileEditor("data.csv")), backStack)
  }

  @Test
  fun openingADifferentFileStacksOnTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("a.csv"))

    backStack.openFile("b.csv")

    assertEquals(listOf(Explorer, Explorer, FileEditor("a.csv"), FileEditor("b.csv")), backStack)
  }

  /**
   * The two document kinds stack on each other - a notebook and a plain file are both main-area
   * tabs, and neither closes the other.
   */
  @Test
  fun openingANotebookStacksOnAPlainFileAndViceVersa() {
    val fromFile = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("a.py"))
    fromFile.openNotebook("nb-1")
    assertEquals(listOf(Explorer, Explorer, FileEditor("a.py"), Editor("nb-1")), fromFile)

    val fromNotebook = mutableListOf<NavKey>(Explorer, Explorer, Editor("nb-1"))
    fromNotebook.openFile("a.py")
    assertEquals(listOf(Explorer, Explorer, Editor("nb-1"), FileEditor("a.py")), fromNotebook)
  }

  /** A terminal opens the same way: a document of its own kind, pushed onto the browser. */
  @Test
  fun openingATerminalPushesItsScreen() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openTerminal("1")

    assertEquals(listOf(Explorer, Terminal("1")), backStack)
  }

  /**
   * A second "new terminal" tap always opens a fresh session - two terminals never compare equal
   * by name, since the server mints a new one every time - so it stacks, the way the reference's
   * `terminal:create-new` adds a second tab rather than revealing the first.
   */
  @Test
  fun openingASecondTerminalStacksOnTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Terminal("1"))

    backStack.openTerminal("2")

    assertEquals(listOf(Explorer, Terminal("1"), Terminal("2")), backStack)
  }

  /** A terminal stacks on a document exactly as the other two kinds stack on each other. */
  @Test
  fun openingATerminalStacksOnAnEditorAndViceVersa() {
    val fromEditor = mutableListOf<NavKey>(Explorer, Explorer, Editor("nb-1"))
    fromEditor.openTerminal("1")
    assertEquals(listOf(Explorer, Explorer, Editor("nb-1"), Terminal("1")), fromEditor)

    val fromTerminal = mutableListOf<NavKey>(Explorer, Explorer, Terminal("1"))
    fromTerminal.openNotebook("nb-1")
    assertEquals(listOf(Explorer, Explorer, Terminal("1"), Editor("nb-1")), fromTerminal)
  }

  /**
   * The same notebook, already open **beneath** whatever is on top, is promoted rather than
   * duplicated - `docs/PLAN.md`'s own Open risks entry on `openNotebook`: `lastOrNull() == document`
   * only ever checked the top, so a notebook one level deeper survived a swap of what sat above it
   * and came back a second time on the next [openNotebook] for the same id. Two entries would then
   * share one `contentKey` ([openNotebook]'s own doc comment), and popping either would clear
   * `ViewModelStore`/`SaveableStateHolder` state the other was still relying on (P6b-0).
   */
  @Test
  fun reopeningANotebookStackedBeneathSomethingElsePromotesItInstead() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Terminal("1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Terminal("1"), Editor("nb-1")), backStack)
  }

  /**
   * A Console stacks on top of the notebook it shares a kernel with - P6b-9's own reason to
   * exist: Back has to return to the still-open notebook, not to the browser. [openConsole] is
   * not [openNotebook]'s twin for that reason.
   */
  @Test
  fun openingAConsolePushesItOnTopOfTheOpenNotebook() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openConsole("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1"), Console("nb-1")), backStack)
  }

  @Test
  fun openingTheConsoleAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Console("nb-1"))

    backStack.openConsole("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1"), Console("nb-1")), backStack)
  }

  /**
   * A document opened while a Console shows leaves the console's whole stack beneath it: the
   * console is a main-area widget like any other now, and so is the notebook it was attached to.
   */
  @Test
  fun openingADifferentNotebookWhileAConsoleIsOpenLeavesEverythingBeneath() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Console("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-1"), Console("nb-1"), Editor("nb-2")), backStack)
  }

  /**
   * Reopening the notebook a Console is stacked over promotes the notebook **past** the console -
   * reveal moves it to the top, and the console stays open beneath, the way the reference keeps
   * an attached console's tab when its notebook's is activated.
   */
  @Test
  fun reopeningTheNotebookUnderAnOpenConsolePromotesItPastTheConsole() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Console("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Console("nb-1"), Editor("nb-1")), backStack)
  }

  /** Settings, opened with nothing else showing, pushes onto the bare browser. */
  @Test
  fun openingSettingsPushesItsScreen() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openSettings()

    assertEquals(listOf(Explorer, Settings), backStack)
  }

  @Test
  fun openingSettingsAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer, Settings)

    backStack.openSettings()

    assertEquals(listOf(Explorer, Settings), backStack)
  }

  /**
   * Settings stacks on top of an open notebook rather than replacing it - [openConsole]'s own
   * reason: visiting it should not dispose the document underneath, so Back has to return there.
   */
  @Test
  fun openingSettingsStacksOnTopOfAnOpenNotebook() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openSettings()

    assertEquals(listOf(Explorer, Editor("nb-1"), Settings), backStack)
  }

  /**
   * A document opened while Settings shows leaves both beneath - Settings is a main-area widget
   * too, and the document it sat on is still an open tab.
   */
  @Test
  fun openingANotebookWhileSettingsIsOpenLeavesBothBeneath() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Settings)

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-1"), Settings, Editor("nb-2")), backStack)
  }

  /** The Launcher is a detail-pane destination: it pushes onto the bare browser. */
  @Test
  fun openingTheLauncherPushesIt() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openLauncher()

    assertEquals(listOf(Explorer, Launcher), backStack)
  }

  @Test
  fun openingTheLauncherAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer, Launcher)

    backStack.openLauncher()

    assertEquals(listOf(Explorer, Launcher), backStack)
  }

  /**
   * Reaching the launcher stacks it on top of an open document rather than replacing it -
   * `openSettings`'s own shape, since visiting it must not dispose the document underneath. The
   * reference adds it as a main-area *tab* beside the open ones (`launcher:create`).
   */
  @Test
  fun openingTheLauncherStacksOnTopOfAnOpenDocument() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openLauncher()

    assertEquals(listOf(Explorer, Editor("nb-1"), Launcher), backStack)
  }

  /**
   * At most one Launcher ever exists (the reference's own rule, `maybeCreate`'s). A `data object`
   * key shares `contentKey` across equal entries, so a second `Launcher` beneath anything stacked
   * over it - `[Explorer, Launcher, Settings]` - must come forward rather than be duplicated, the
   * same invariant P6b-0 gave documents.
   */
  @Test
  fun openingTheLauncherWithOneDeeperInTheStackPromotesIt() {
    val backStack = mutableListOf<NavKey>(Explorer, Launcher, Settings)

    backStack.openLauncher()

    assertEquals(listOf(Explorer, Settings, Launcher), backStack)
  }

  /**
   * The Launcher is a tab like any other main-area widget: opening a document **from the browser**
   * does not close it - `launcher.dispose()` runs only when the launcher itself spawned the
   * widget (`add(item, 'main', {ref: id})`, `launcher-extension/src/index.ts:95-106`), which is
   * the launch handler's rule, not the opener's.
   */
  @Test
  fun openingANotebookFromTheListLeavesTheLauncherOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Launcher)

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Launcher, Editor("nb-1")), backStack)
  }

  /** The launcher stays open even with documents stacked between. */
  @Test
  fun openingAFileLeavesALauncherStackedOverTheOpenDocument() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Launcher)

    backStack.openFile("a.py")

    assertEquals(listOf(Explorer, Editor("nb-1"), Launcher, FileEditor("a.py")), backStack)
  }

  /**
   * `shell.isEmpty('main')` - the question `maybeCreate` asks. A lone browser is an empty main
   * area; a Launcher, a document, or anything stacked in the detail slot is not.
   */
  @Test
  fun theMainAreaIsEmptyOnlyWhenNoDetailEntryExists() {
    assertEquals(true, listOf<NavKey>(Explorer).mainAreaIsEmpty())
    assertEquals(false, listOf<NavKey>(Explorer, Launcher).mainAreaIsEmpty())
    assertEquals(false, listOf<NavKey>(Explorer, Editor("nb-1")).mainAreaIsEmpty())
    assertEquals(false, listOf<NavKey>(Explorer, Launcher, Settings).mainAreaIsEmpty())
  }

  /**
   * `main.title.closable = Array.from(labShell.widgets('main')).length > 1`
   * (`launcher-extension/src/index.ts:126`) - a launcher that is the *only* thing in the main area
   * gets no close affordance, because there is nothing beneath it to return to. The count is of
   * main widgets, so a document or the Settings underneath it is what makes it closable.
   */
  @Test
  fun aLauncherIsClosableOnlyWhileAnotherMainWidgetExists() {
    assertEquals(false, listOf<NavKey>(Explorer).launcherIsClosable())
    assertEquals(false, listOf<NavKey>(Explorer, Launcher).launcherIsClosable())
    assertEquals(true, listOf<NavKey>(Explorer, Editor("nb-1"), Launcher).launcherIsClosable())
    assertEquals(true, listOf<NavKey>(Explorer, Launcher, Settings).launcherIsClosable())
  }

  /**
   * A Launcher-made Console is a document in its own right - `shell.add(panel, 'main')`
   * (`console-extension/src/index.ts`), not [openConsole]'s stack-on-top - and the launcher that
   * minted it is disposed by the launch handler, not by this opener.
   */
  @Test
  fun aStandaloneConsoleStacksOnTheDetail() {
    val backStack = mutableListOf<NavKey>(Explorer, Launcher)

    backStack.openStandaloneConsole("console-1-x", "Console 1")

    assertEquals(
      listOf(Explorer, Launcher, Console("console-1-x", "Console 1", standalone = true)),
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
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Console("nb-1"))

    backStack.openStandaloneConsole("console-1-x", "Console 1")

    assertEquals(
      listOf(Explorer, Editor("nb-1"), Console("console-1-x", "Console 1", standalone = true)),
      backStack,
    )
  }

  @Test
  fun aSecondStandaloneConsoleReplacesTheFirst() {
    val backStack =
      mutableListOf<NavKey>(Explorer, Console("console-1-x", "Console 1", standalone = true))

    backStack.openStandaloneConsole("console-2-y", "Console 2")

    assertEquals(listOf(Explorer, Console("console-2-y", "Console 2", standalone = true)), backStack)
  }

  @Test
  fun openingAnAttachedConsoleSweepsAStandaloneOneFromTheStack() {
    val backStack = mutableListOf<NavKey>(
      Explorer,
      Editor("nb-1"),
      Console("console-1-x", "Console 1", standalone = true),
    )

    backStack.openConsole("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1"), Console("nb-1")), backStack)
  }
}
