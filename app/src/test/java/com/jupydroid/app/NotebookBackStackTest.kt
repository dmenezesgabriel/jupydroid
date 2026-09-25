package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reveal-don't-reopen rule, on its own, where the cases are cheap to state.
 *
 * The device half is `NotebookNavigationTest.tappingTheSameNotebookTwiceOpensOneEditor`; this is
 * the half that says what "already open" means when it is not the notebook on top.
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
   * A second notebook **replaces** the first rather than stacking on it.
   *
   * It used to push, which was unreachable on a phone - Explorer is not on screen while an editor
   * is - and so never wrong. Two panes make it reachable: the list is beside the editor, and a
   * stacked `[Explorer, Editor(a), Editor(b)]` would pair the *last two* entries and put a notebook
   * where the list belongs. Replacing also disposes `Editor(a)`'s entry, which is what makes
   * `EditorViewModel.onCleared` run and close its kernel socket.
   */
  @Test
  fun openingADifferentNotebookReplacesTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-2")), backStack)
  }

  /** Only an editor is swapped out. Anything else below stays where it is. */
  @Test
  fun openingFromTheListLeavesTheListInPlace() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openNotebook("nb-1")
    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-2")), backStack)
  }

  /** A plain file opens the same way a notebook does: one document, pushed onto the browser. */
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

  /** A second file replaces the one open - the reference's `openOrReveal`, applied to files. */
  @Test
  fun openingADifferentFileReplacesTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("a.csv"))

    backStack.openFile("b.csv")

    assertEquals(listOf(Explorer, Explorer, FileEditor("b.csv")), backStack)
  }

  /**
   * The two document kinds swap each other. The explorer can open both, and a detail it cannot
   * swap would stack `[Explorer, FileEditor(a), Editor(b)]` - two entries claiming one pane, of
   * which only the last is ever drawn.
   */
  @Test
  fun openingANotebookReplacesAPlainFileAndViceVersa() {
    val fromFile = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("a.py"))
    fromFile.openNotebook("nb-1")
    assertEquals(listOf(Explorer, Explorer, Editor("nb-1")), fromFile)

    val fromNotebook = mutableListOf<NavKey>(Explorer, Explorer, Editor("nb-1"))
    fromNotebook.openFile("a.py")
    assertEquals(listOf(Explorer, Explorer, FileEditor("a.py")), fromNotebook)
  }

  /** A terminal opens the same way: one document, pushed onto the browser. */
  @Test
  fun openingATerminalPushesItsScreen() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openTerminal("1")

    assertEquals(listOf(Explorer, Terminal("1")), backStack)
  }

  /**
   * A second "new terminal" tap always opens a fresh session - two terminals never compare equal
   * by name, since the server mints a new one every time - so it replaces rather than reveals.
   */
  @Test
  fun openingASecondTerminalReplacesTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Terminal("1"))

    backStack.openTerminal("2")

    assertEquals(listOf(Explorer, Terminal("2")), backStack)
  }

  /** A terminal swaps a document exactly as the other two kinds swap each other. */
  @Test
  fun openingATerminalReplacesAnEditorAndViceVersa() {
    val fromEditor = mutableListOf<NavKey>(Explorer, Explorer, Editor("nb-1"))
    fromEditor.openTerminal("1")
    assertEquals(listOf(Explorer, Explorer, Terminal("1")), fromEditor)

    val fromTerminal = mutableListOf<NavKey>(Explorer, Explorer, Terminal("1"))
    fromTerminal.openNotebook("nb-1")
    assertEquals(listOf(Explorer, Explorer, Editor("nb-1")), fromTerminal)
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

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
  }

  /**
   * A Console stacks on top of the notebook it shares a kernel with, rather than replacing it -
   * P6b-9's own reason to exist: Back has to return to the still-open notebook, not to the
   * browser. [openConsole] is not [openNotebook]'s twin for that reason; it never goes through
   * [chooseDetailThatFitsTheSurface] at all.
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
   * The generalised case [chooseDetailThatFitsTheSurface]'s single `if` could not answer: a
   * Console stacks a *second* document on top of its notebook, so unwinding to the surface before
   * opening something else has to remove both, not just the one on top. A single `removeAt` left
   * the notebook behind - `[Explorer, Editor("nb-1"), Editor("nb-2")]`, two documents claiming one
   * pane, the exact shape P6b-0 exists to prevent - which is why this loops now.
   */
  @Test
  fun openingADifferentNotebookWhileAConsoleIsOpenReplacesBothItAndItsNotebook() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Console("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-2")), backStack)
  }

  /** Reopening the notebook a Console is showing over promotes it and drops the Console. */
  @Test
  fun reopeningTheNotebookUnderAnOpenConsoleDropsTheConsole() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Console("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
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
   * The generalised sweep [openConsole]'s own test proves for Console applies to Settings too:
   * opening a different document while Settings sits on top must not leave the old notebook
   * behind underneath it.
   */
  @Test
  fun openingADifferentNotebookWhileSettingsIsOpenReplacesBothItAndTheDocumentBeneath() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Settings)

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-2")), backStack)
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
   * A document opened from the launcher replaces it in place - `add(item, 'main', {ref: id})` →
   * `launcher.dispose()` (`launcher-extension/src/index.ts:95-106`). `Launcher` is in
   * `isDocument()` for this: anything a surface opens sweeps the detail it was opened from.
   */
  @Test
  fun openingANotebookReplacesTheLauncher() {
    val backStack = mutableListOf<NavKey>(Explorer, Launcher)

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
  }

  /** The sweep reaches a launcher stacked *over* the open document, not only a lone one. */
  @Test
  fun openingAFileSweepsALauncherStackedOverTheOpenDocument() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Launcher)

    backStack.openFile("a.py")

    assertEquals(listOf(Explorer, FileEditor("a.py")), backStack)
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
   * (`console-extension/src/index.ts`), not [openConsole]'s stack-on-top - so it takes the detail
   * the way a notebook does, the launcher that minted it included.
   */
  @Test
  fun aStandaloneConsoleReplacesTheDetailIncludingTheLauncher() {
    val backStack = mutableListOf<NavKey>(Explorer, Launcher)

    backStack.openStandaloneConsole("console-1-x", "Console 1")

    assertEquals(listOf(Explorer, Console("console-1-x", "Console 1", standalone = true)), backStack)
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

    assertEquals(listOf(Explorer, Console("console-1-x", "Console 1", standalone = true)), backStack)
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
