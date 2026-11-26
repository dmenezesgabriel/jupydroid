package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edit-mode half of JupyterLab's keybinding table
 * (`packages/notebook-extension/schema/tracker.json`, `notebook:run` /
 * `notebook:run-and-advance` / `notebook:run-and-insert` rows).
 *
 * Command-mode rows are a later task and are asserted absent here so the table
 * does not grow one silently.
 */
class NotebookKeymapTest {
  private val edit = CellMode.EDIT
  private val command = CellMode.COMMAND

  /** `Accel Enter` on the editing selector → `notebook:run` (`tracker.json:1754-1759`). */
  @Test
  fun ctrlEnter_inEdit_runsTheCell() {
    assertEquals(
      NotebookChordIntent.RunSelected,
      notebookChord(edit, NotebookChord(Key.Enter, ctrl = true)),
    )
  }

  /** `Shift Enter` on the editing selector → `notebook:run-and-advance` (`tracker.json:1761-1766`). */
  @Test
  fun shiftEnter_inEdit_runsAndAdvances() {
    assertEquals(
      NotebookChordIntent.RunAndAdvance,
      notebookChord(edit, NotebookChord(Key.Enter, shift = true)),
    )
  }

  /** `Alt Enter` on the editing selector → `notebook:run-and-insert` (`tracker.json:1768-1773`). */
  @Test
  fun altEnter_inEdit_runsAndInserts() {
    assertEquals(
      NotebookChordIntent.RunAndInsert,
      notebookChord(edit, NotebookChord(Key.Enter, alt = true)),
    )
  }

  /**
   * Which intents drop the caret is a property of the intent, not of the dispatch site, so the
   * handler reads it rather than re-knowing it. `runSelected` and `runAndAdvance` both begin
   * `notebook.mode = 'command'` (`packages/notebook/src/actions.tsx:822, 870`); ours keeps the
   * caret only as the user-confirmed deviation named on [RunSelected].
   */
  @Test
  fun whichIntentsDropTheCaret_isAPropertyOfTheIntent() {
    assertFalse(NotebookChordIntent.RunSelected.dropsCaret)
    assertTrue(NotebookChordIntent.RunAndAdvance.dropsCaret)
    assertTrue(NotebookChordIntent.RunAndInsert.dropsCaret)
  }

  /** The modifier flag alone is the match - the run column does not care what the others hold. */
  @Test
  fun ctrlEnter_withAltOrShiftHeld_doesNotMatch() {
    assertNull(notebookChord(edit, NotebookChord(Key.Enter, ctrl = true, shift = true)))
    assertNull(notebookChord(edit, NotebookChord(Key.Enter, ctrl = true, alt = true)))
  }

  @Test
  fun shiftEnter_withCtrlHeld_doesNotMatch() {
    assertNull(notebookChord(edit, NotebookChord(Key.Enter, shift = true, ctrl = true)))
  }

  @Test
  fun altEnter_withCtrlHeld_doesNotMatch() {
    assertNull(notebookChord(edit, NotebookChord(Key.Enter, alt = true, ctrl = true)))
  }

  /** An unmodified Enter is the editor's own newline; the keymap must not claim it. */
  @Test
  fun bareEnter_staysANewline() {
    assertNull(notebookChord(edit, NotebookChord(Key.Enter)))
    assertNull(notebookChord(command, NotebookChord(Key.Enter)))
  }

  /** The run chords are editor-scoped rows; in command mode none of the three fires. */
  @Test
  fun theThreeChords_doNotFireInCommandMode() {
    assertNull(notebookChord(command, NotebookChord(Key.Enter, ctrl = true)))
    assertNull(notebookChord(command, NotebookChord(Key.Enter, shift = true)))
    assertNull(notebookChord(command, NotebookChord(Key.Enter, alt = true)))
  }

  /**
   * Keyboards split Enter into two keycodes, and the table keys on the *key* rather than the
   * hardware that sent it, so both resolves (the normalisation the Compose wrapper performs -
   * `KeyEvent.chord` maps `KEYCODE_NUMPAD_ENTER` onto `Key.Enter`).
   */
  @Test
  fun numpadEnter_resolvesAsEnter() {
    assertEquals(
      NotebookChordIntent.RunSelected,
      notebookChord(edit, NotebookChord(Key.NumPadEnter, ctrl = true)),
    )
  }

  /** Modifier chords that mean nothing fall through to the editor. */
  @Test
  fun unmatchedChords_resolveToNothing() {
    assertNull(notebookChord(edit, NotebookChord(Key.Enter, meta = true)))
    assertNull(notebookChord(edit, NotebookChord(Key.A, ctrl = true, shift = true)))
  }
}
