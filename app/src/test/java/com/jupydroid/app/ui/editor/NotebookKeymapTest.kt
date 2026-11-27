package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mode-crossing rows of JupyterLab's keybinding table
 * (`packages/notebook-extension/schema/tracker.json`, `notebook:run` /
 * `notebook:run-and-advance` / `notebook:run-and-insert` /
 * `notebook:enter-edit-mode` / `notebook:enter-command-mode`), plus the command-mode cursor
 * rows: `notebook:move-cursor-{up,down}`, `notebook:extend-marked-cells-*` and the two
 * `notebook:move-cursor-heading-*` commands.
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
    assertFalse(NotebookChordIntent.EnterEditMode.dropsCaret)
    assertFalse(NotebookChordIntent.EnterCommandMode.dropsCaret)
  }

  /**
   * `Enter` on `.jp-Cell:focus` → `notebook:enter-edit-mode` (`tracker.json:443-448`). Ours is the
   * command-mode row that asks for the caret; the focus event it produces is what turns the mode.
   */
  @Test
  fun enter_inCommand_entersEditMode() {
    assertEquals(NotebookChordIntent.EnterEditMode, notebookChord(command, NotebookChord(Key.Enter)))
  }

  /** `ArrowUp`/`K` in command mode → `notebook:move-cursor-up` (`tracker.json:523-530`). */
  @Test
  fun arrowUp_andK_inCommand_moveTheCursorUp() {
    assertEquals(
      NotebookChordIntent.SelectAbove,
      notebookChord(command, NotebookChord(Key.DirectionUp)),
    )
    assertEquals(
      NotebookChordIntent.SelectAbove,
      notebookChord(command, NotebookChord(Key.K)),
    )
  }

  /** `ArrowDown`/`J` in command mode → `notebook:move-cursor-down` (`tracker.json:533-540`). */
  @Test
  fun arrowDown_andJ_inCommand_moveTheCursorDown() {
    assertEquals(
      NotebookChordIntent.SelectBelow,
      notebookChord(command, NotebookChord(Key.DirectionDown)),
    )
    assertEquals(
      NotebookChordIntent.SelectBelow,
      notebookChord(command, NotebookChord(Key.J)),
    )
  }

  /** `ArrowLeft` → `notebook:move-cursor-heading-above-or-collapse` (`tracker.json:543-546`). */
  @Test
  fun arrowLeft_inCommand_selectsHeadingAboveOrCollapses() {
    assertEquals(
      NotebookChordIntent.SelectHeadingAboveOrCollapse,
      notebookChord(command, NotebookChord(Key.DirectionLeft)),
    )
  }

  /** `ArrowRight` → `notebook:move-cursor-heading-below-or-expand` (`tracker.json:548-551`). */
  @Test
  fun arrowRight_inCommand_selectsHeadingBelowOrExpands() {
    assertEquals(
      NotebookChordIntent.SelectHeadingBelowOrExpand,
      notebookChord(command, NotebookChord(Key.DirectionRight)),
    )
  }

  /**
   * `Shift`+`ArrowUp`/`K` → `extend-marked-cells-above`, `Shift`+`ArrowDown`/`J` → `-below`
   * (`tracker.json:554-572`). Shift is part of the chord, which is what separates the extends
   * from the cursor moves the same keys carry unshifted.
   */
  @Test
  fun shiftArrowsAndJK_inCommand_extendTheMarkedRun() {
    assertEquals(
      NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE),
      notebookChord(command, NotebookChord(Key.DirectionUp, shift = true)),
    )
    assertEquals(
      NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE),
      notebookChord(command, NotebookChord(Key.K, shift = true)),
    )
    assertEquals(
      NotebookChordIntent.ExtendMarked(SelectionReach.BELOW),
      notebookChord(command, NotebookChord(Key.DirectionDown, shift = true)),
    )
    assertEquals(
      NotebookChordIntent.ExtendMarked(SelectionReach.BELOW),
      notebookChord(command, NotebookChord(Key.J, shift = true)),
    )
  }

  /** `Shift`+`Home`/`End` → `extend-marked-cells-top`/`-bottom` (`tracker.json:575-582`). */
  @Test
  fun shiftHomeAndEnd_inCommand_extendToTheEnds() {
    assertEquals(
      NotebookChordIntent.ExtendMarked(SelectionReach.TOP),
      notebookChord(command, NotebookChord(Key.MoveHome, shift = true)),
    )
    assertEquals(
      NotebookChordIntent.ExtendMarked(SelectionReach.BOTTOM),
      notebookChord(command, NotebookChord(Key.MoveEnd, shift = true)),
    )
  }

  /** The cursor rows are command-scoped: every one of them stays the field's own key in edit mode. */
  @Test
  fun theCursorRows_doNotFireInEditMode() {
    assertNull(notebookChord(edit, NotebookChord(Key.DirectionUp)))
    assertNull(notebookChord(edit, NotebookChord(Key.DirectionDown)))
    assertNull(notebookChord(edit, NotebookChord(Key.DirectionLeft)))
    assertNull(notebookChord(edit, NotebookChord(Key.DirectionRight)))
    assertNull(notebookChord(edit, NotebookChord(Key.J)))
    assertNull(notebookChord(edit, NotebookChord(Key.K)))
    assertNull(notebookChord(edit, NotebookChord(Key.J, shift = true)))
    assertNull(notebookChord(edit, NotebookChord(Key.MoveHome, shift = true)))
    assertNull(notebookChord(edit, NotebookChord(Key.MoveEnd, shift = true)))
  }

  /** Unshifted `Home`/`End` are bound nowhere upstream - the field's caret keeps them. */
  @Test
  fun bareHomeAndEnd_resolveToNothing() {
    assertNull(notebookChord(command, NotebookChord(Key.MoveHome)))
    assertNull(notebookChord(command, NotebookChord(Key.MoveEnd)))
  }

  /**
   * `Escape` on the editing selector → `notebook:enter-command-mode` (`tracker.json:457-462`).
   * JupyterLab's implementation "just unfocuses the editor"; here unfocusing is a focus *move*,
   * so the intent reaches the dispatch site rather than a state transform.
   */
  @Test
  fun escape_inEdit_entersCommandMode() {
    assertEquals(NotebookChordIntent.EnterCommandMode, notebookChord(edit, NotebookChord(Key.Escape)))
  }

  /** `Ctrl M` is the alternate binding of the same command (`tracker.json:464-469`). */
  @Test
  fun ctrlM_inEdit_entersCommandMode() {
    assertEquals(
      NotebookChordIntent.EnterCommandMode,
      notebookChord(edit, NotebookChord(Key.M, ctrl = true)),
    )
  }

  /** The mode-crossing rows are mode-scoped: neither fires from the wrong side. */
  @Test
  fun theModeRows_doNotFireFromTheWrongMode() {
    assertNull(notebookChord(command, NotebookChord(Key.Escape)))
    assertNull(notebookChord(command, NotebookChord(Key.M, ctrl = true)))
    assertNull(notebookChord(edit, NotebookChord(Key.Enter)))
  }

  /** An `Escape` carrying a modifier is not the binding. */
  @Test
  fun escape_withAModifierHeld_doesNotMatch() {
    assertNull(notebookChord(edit, NotebookChord(Key.Escape, shift = true)))
    assertNull(notebookChord(edit, NotebookChord(Key.Escape, ctrl = true)))
  }

  /** A `Ctrl M` carrying another modifier is not the binding either. */
  @Test
  fun ctrlM_withShiftOrAltHeld_doesNotMatch() {
    assertNull(notebookChord(edit, NotebookChord(Key.M, ctrl = true, shift = true)))
    assertNull(notebookChord(edit, NotebookChord(Key.M, ctrl = true, alt = true)))
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

  /** An unmodified Enter on a field is the editor's own newline; the keymap must not claim it. */
  @Test
  fun bareEnter_staysANewline() {
    assertNull(notebookChord(edit, NotebookChord(Key.Enter)))
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
