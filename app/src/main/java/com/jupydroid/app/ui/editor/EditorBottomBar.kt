package com.jupydroid.app.ui.editor

/** Which bar the notebook keeps along its bottom edge - see [editorBottomBar]. */
enum class EditorBottomBar {
  /** [NotebookBar]: what you ask the kernel, which is what a notebook being read wants. */
  KERNEL,

  /** [SymbolRow]: the characters a touch keyboard lacks, which is what a cell being typed wants. */
  SYMBOLS,

  /** Nothing, because the contextual bar at the top is what the notebook is being used for. */
  NONE,

  /** [DebuggerControlBar]: `next`/`stepIn`/`stepOut`/`continue`, while a real kernel is paused. */
  DEBUGGER,
}

/**
 * One bar at a time, and which one is the mode's answer.
 *
 * Typing wants the characters a touch keyboard lacks and a notebook being read wants the kernel;
 * showing both would stack two bars over a keyboard that already owns half the screen.
 *
 * **A marked run gets neither, and P2d-3 is what made that necessary.** [SelectionTopBar]'s Run
 * acts on the marked run while [NotebookBar]'s acts on every cell - two bare `PlayArrow` triangles
 * on one screen, one above the other at 320dp (`evidence/p2d-3-run-the-marked-run/`). Dropping the
 * kernel bar is P2c's own rule followed through: *one* surface acts on the run, which is already
 * why the cell's `⋮` is hidden while that bar is up. Nothing the kernel bar offers is what somebody
 * picking cells is doing, and every one of them is a tap on `✕` away.
 *
 * **A notebook being searched gets neither either, for the same two reasons and one measured
 * one.** [FindTopBar] is what a search acts through, nothing the kernel bar offers is what
 * somebody looking for a word is doing - and the find box holds the caret while the notebook is
 * in *command* mode, which is a state nothing before P2i could reach. Without this branch a
 * 320dp-tall landscape phone stacks a find bar, a kernel bar and the soft keyboard over the cell
 * list and `Scaffold` resolves the overflow by giving it zero height, which is the U6 defect
 * exactly (`evidence/u6-ime-layout/640dp/`, and [editorTopBarVisible] for its other half).
 *
 * **A paused kernel beats all three (P6a-9b).** Unlike marking or searching, which the user chose
 * and can leave at will, a breakpoint stop is the kernel itself demanding attention - both
 * references surface their own debug toolbar the instant a session stops regardless of what else
 * was on screen (`packages/debugger-extension/src/index.ts:1170-1264`, every one of
 * `debugContinue`/`next`/`stepIn`/`stepOut` gated on `hasStoppedThreads()` alone).
 *
 * ```
 * when (editorBottomBar(state.selecting, state.mode, state.search != null, state.stoppedAt != null)) { … }
 * ```
 */
internal fun editorBottomBar(
  selecting: Boolean,
  mode: CellMode,
  searching: Boolean,
  stopped: Boolean,
): EditorBottomBar = when {
  stopped -> EditorBottomBar.DEBUGGER
  // First, so the one state that cannot arise - entering edit mode deselects (`widget.ts:1925`) -
  // still has a defined answer rather than depending on which branch is written above the other.
  selecting || searching -> EditorBottomBar.NONE
  mode == CellMode.EDIT -> EditorBottomBar.SYMBOLS
  else -> EditorBottomBar.KERNEL
}
