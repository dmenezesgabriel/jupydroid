package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which bar the notebook keeps along its bottom edge, as a pure rule.
 *
 * Beside [EditorTopBarVisibilityTest] and for the same reason: what chrome a screen keeps is
 * decided by something a test can call, not discovered from a screenshot.
 */
class EditorBottomBarTest {
  @Test
  fun aNotebookBeingReadKeepsTheKernelBar() {
    assertEquals(
      EditorBottomBar.KERNEL,
      editorBottomBar(selecting = false, mode = CellMode.COMMAND, searching = false, stopped = false),
    )
  }

  /**
   * Typing wants the characters a touch keyboard lacks; showing both would stack two bars over a
   * keyboard that already owns half the screen.
   */
  @Test
  fun aCellBeingTypedIntoGetsTheSymbolRow() {
    assertEquals(
      EditorBottomBar.SYMBOLS,
      editorBottomBar(selecting = false, mode = CellMode.EDIT, searching = false, stopped = false),
    )
  }

  /**
   * `symbolRowShown` is `true` by default - see `EditorBottomBarTest` above and
   * `EditorSettings.symbolRowShown`'s own doc comment.
   */
  @Test
  fun aCellBeingTypedIntoWithTheSymbolRowShownGetsTheSymbolRow() {
    assertEquals(
      EditorBottomBar.SYMBOLS,
      editorBottomBar(
        selecting = false,
        mode = CellMode.EDIT,
        searching = false,
        stopped = false,
        symbolRowShown = true,
      ),
    )
  }

  /** `docs/PLAN.md`'s P6c-7 - the Settings row that turns this off. */
  @Test
  fun aCellBeingTypedIntoWithTheSymbolRowHiddenGetsNoBar() {
    assertEquals(
      EditorBottomBar.NONE,
      editorBottomBar(
        selecting = false,
        mode = CellMode.EDIT,
        searching = false,
        stopped = false,
        symbolRowShown = false,
      ),
    )
  }

  /**
   * **And a marked run gets neither, which P2d-3 is what made necessary.** The contextual bar's Run
   * acts on the marked run and the kernel bar's acts on every cell, and until P2d-3 there was no
   * clash to have: both are a bare `PlayArrow`, and at 320dp they sat one above the other on the
   * same screen (`evidence/p2d-3-run-the-marked-run/`). Dropping the kernel bar is P2c's own rule
   * followed through - *one* surface acts on the run, which is why the cell's `⋮` is already hidden
   * while it is up - and none of what the kernel bar offers is what somebody picking cells is doing.
   */
  @Test
  fun aMarkedRunGetsNeitherBar() {
    assertEquals(
      EditorBottomBar.NONE,
      editorBottomBar(selecting = true, mode = CellMode.COMMAND, searching = false, stopped = false),
    )
  }

  /**
   * **And a notebook being searched gets neither, which is the one state before P2i nothing could
   * reach: the caret is in the find box, so the notebook is in *command* mode with the soft
   * keyboard up.** Left as KERNEL, a 320dp-tall landscape phone stacks a find bar, a kernel bar and
   * the keyboard, and `Scaffold` hands the cell list zero height - the U6 defect, which no test
   * without an IME can see and which is why this rule is asserted rather than looked at.
   */
  @Test
  fun aNotebookBeingSearchedGetsNeitherBar() {
    assertEquals(
      EditorBottomBar.NONE,
      editorBottomBar(selecting = false, mode = CellMode.COMMAND, searching = true, stopped = false),
    )
  }

  /**
   * Entering edit mode drops the run - *"Edit mode deselects all cells"* (`widget.ts:1925-1929`) -
   * so this state cannot arise. It is pinned anyway, because a rule with an unreachable case is one
   * refactor away from having a reachable one, and marking wins: the selection bar is on screen.
   */
  @Test
  fun markingBeatsEditingIfTheTwoEverDisagree() {
    assertEquals(
      EditorBottomBar.NONE,
      editorBottomBar(selecting = true, mode = CellMode.EDIT, searching = false, stopped = false),
    )
  }

  /**
   * **A paused kernel (P6a-9b) beats every other state.** Unlike a marked run or a search, which
   * the user chose and can leave at will, a breakpoint stop is the kernel itself demanding
   * attention - both references surface their own debug toolbar the instant a session stops,
   * regardless of what else was on screen (`packages/debugger-extension/src/index.ts:1170-1264`,
   * every one of `debugContinue`/`next`/`stepIn`/`stepOut` gated on `hasStoppedThreads()` alone).
   */
  @Test
  fun aStoppedKernelGetsTheDebuggerBarEvenWhileMarkingOrSearching() {
    assertEquals(
      EditorBottomBar.DEBUGGER,
      editorBottomBar(selecting = false, mode = CellMode.COMMAND, searching = false, stopped = true),
    )
    assertEquals(
      EditorBottomBar.DEBUGGER,
      editorBottomBar(selecting = true, mode = CellMode.COMMAND, searching = false, stopped = true),
    )
    assertEquals(
      EditorBottomBar.DEBUGGER,
      editorBottomBar(selecting = false, mode = CellMode.COMMAND, searching = true, stopped = true),
    )
    assertEquals(
      EditorBottomBar.DEBUGGER,
      editorBottomBar(selecting = false, mode = CellMode.EDIT, searching = false, stopped = true),
    )
  }
}
