package com.jupydroid.app.ui.editor

/**
 * The one door a [NotebookChordIntent] comes through - what [EditorContent]'s
 * `onPreviewKeyEvent` asks of the ViewModel once [notebookChord] has named the command.
 *
 * An extension rather than a member, for [cellAction]'s reason: [EditorViewModel] is at
 * detekt's `TooManyFunctions` cap, and the dispatch needs only [kernel]'s public surface.
 *
 * Every intent is a `runSelected` plus zero or one document moves, which is why the dispatch
 * is three calls and nothing more: [NotebookChordIntent.RunSelected] is the marked run alone,
 * and the other two are the same run with [EditorUiState.withRunAdvanced] /
 * [EditorUiState.withRunInserted] folded in - `actions.tsx:814-856`, `:864-903`.
 */
internal fun EditorViewModel.notebookKey(intent: NotebookChordIntent) {
  when (intent) {
    NotebookChordIntent.RunSelected -> kernel.run(RunRange.MARKED)
    NotebookChordIntent.RunAndAdvance -> kernel.chords.runAndAdvance()
    NotebookChordIntent.RunAndInsert -> kernel.chords.runAndInsert()
  }
}
