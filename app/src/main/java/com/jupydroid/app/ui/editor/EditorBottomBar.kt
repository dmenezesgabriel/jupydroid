package com.jupydroid.app.ui.editor

/** Which bar the notebook keeps along its bottom edge - see [editorBottomBar]. */
enum class EditorBottomBar {
  /** [NotebookBar]: what you ask the kernel, which is what a notebook being read wants. */
  KERNEL,

  /** [SymbolRow]: the characters a touch keyboard lacks, which is what a cell being typed wants. */
  SYMBOLS,

  /** Nothing, because [SelectionTopBar] is what acts on a marked run. */
  NONE,
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
 * ```
 * when (editorBottomBar(state.selecting, state.mode)) { … }
 * ```
 */
internal fun editorBottomBar(selecting: Boolean, mode: CellMode): EditorBottomBar = when {
  // First, so the one state that cannot arise - entering edit mode deselects (`widget.ts:1925`) -
  // still has a defined answer rather than depending on which branch is written above the other.
  selecting -> EditorBottomBar.NONE
  mode == CellMode.EDIT -> EditorBottomBar.SYMBOLS
  else -> EditorBottomBar.KERNEL
}
