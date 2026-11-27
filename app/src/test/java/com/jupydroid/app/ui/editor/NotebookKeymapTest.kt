package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.ui.editor.NotebookChordResolution.Bound
import com.jupydroid.app.ui.editor.NotebookChordResolution.Pending
import com.jupydroid.app.ui.editor.NotebookChordResolution.Unbound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a hardware event resolves against JupyterLab's keybinding table - the resolver's half of
 * `packages/notebook-extension/schema/tracker.json`: mode scoping, exact modifiers, and Lumino's
 * `matchKeystroke` semantics, which is what makes `D`,`D` a two-stroke binding
 * (`packages/apputils/src/keybinding.ts`'s partial match).
 *
 * The rows themselves are tabled in `NotebookChordTable.kt` and proven one by one in
 * [NotebookChordTableTest]; this file proves the machine they resolve through.
 */
class NotebookKeymapTest {
  private val edit = CellMode.EDIT
  private val command = CellMode.COMMAND

  /**
   * The focused node's half of a row's selector, as the dispatch site computes it: which mode,
   * whether the head cell's *frame* owns focus (`.jp-Cell:focus`, not `:focus-within` - a `⋮`
   * inside the cell is a descendant), and that cell's kind (`.jp-MarkdownCell:focus`).
   */
  private fun context(mode: CellMode, cellFocused: Boolean = true, headKind: CellKind? = null) =
    NotebookKeyContext(mode, cellFocused, headKind)

  /** `Accel Enter` on the editing selector → `notebook:run` (`tracker.json:1754-1759`). */
  @Test
  fun ctrlEnter_inEdit_runsTheCell() {
    assertEquals(
      Bound(NotebookChordIntent.RunSelected),
      notebookChord(context(edit), NotebookChord(Key.Enter, ctrl = true)),
    )
  }

  /** `Shift Enter` on the editing selector → `notebook:run-and-advance` (`tracker.json:1761-1766`). */
  @Test
  fun shiftEnter_inEdit_runsAndAdvances() {
    assertEquals(
      Bound(NotebookChordIntent.RunAndAdvance),
      notebookChord(context(edit), NotebookChord(Key.Enter, shift = true)),
    )
  }

  /** `Alt Enter` on the editing selector → `notebook:run-and-insert` (`tracker.json:1768-1773`). */
  @Test
  fun altEnter_inEdit_runsAndInserts() {
    assertEquals(
      Bound(NotebookChordIntent.RunAndInsert),
      notebookChord(context(edit), NotebookChord(Key.Enter, alt = true)),
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
    assertEquals(
      Bound(NotebookChordIntent.EnterEditMode),
      notebookChord(context(command), NotebookChord(Key.Enter)),
    )
  }

  /**
   * `.jp-Cell:focus`, not `:focus-within` (`tracker.json:447`): `Enter` with a descendant control
   * focused - a `⋮`, say - is that control's own key, so the row does not match.
   */
  @Test
  fun enter_withADescendantFocused_doesNotMatch() {
    assertEquals(
      Unbound,
      notebookChord(context(command, cellFocused = false), NotebookChord(Key.Enter)),
    )
  }

  /** `ArrowUp`/`K` in command mode → `notebook:move-cursor-up` (`tracker.json:523-530`). */
  @Test
  fun arrowUp_andK_inCommand_moveTheCursorUp() {
    assertEquals(
      Bound(NotebookChordIntent.SelectAbove),
      notebookChord(context(command), NotebookChord(Key.DirectionUp)),
    )
    assertEquals(
      Bound(NotebookChordIntent.SelectAbove),
      notebookChord(context(command), NotebookChord(Key.K)),
    )
  }

  /** `ArrowDown`/`J` in command mode → `notebook:move-cursor-down` (`tracker.json:533-540`). */
  @Test
  fun arrowDown_andJ_inCommand_moveTheCursorDown() {
    assertEquals(
      Bound(NotebookChordIntent.SelectBelow),
      notebookChord(context(command), NotebookChord(Key.DirectionDown)),
    )
    assertEquals(
      Bound(NotebookChordIntent.SelectBelow),
      notebookChord(context(command), NotebookChord(Key.J)),
    )
  }

  /** `ArrowLeft` → `notebook:move-cursor-heading-above-or-collapse` (`tracker.json:543-546`). */
  @Test
  fun arrowLeft_inCommand_selectsHeadingAboveOrCollapses() {
    assertEquals(
      Bound(NotebookChordIntent.SelectHeadingAboveOrCollapse),
      notebookChord(context(command), NotebookChord(Key.DirectionLeft)),
    )
  }

  /** `ArrowRight` → `notebook:move-cursor-heading-below-or-expand` (`tracker.json:548-551`). */
  @Test
  fun arrowRight_inCommand_selectsHeadingBelowOrExpands() {
    assertEquals(
      Bound(NotebookChordIntent.SelectHeadingBelowOrExpand),
      notebookChord(context(command), NotebookChord(Key.DirectionRight)),
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
      Bound(NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE)),
      notebookChord(context(command), NotebookChord(Key.DirectionUp, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ExtendMarked(SelectionReach.ABOVE)),
      notebookChord(context(command), NotebookChord(Key.K, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ExtendMarked(SelectionReach.BELOW)),
      notebookChord(context(command), NotebookChord(Key.DirectionDown, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ExtendMarked(SelectionReach.BELOW)),
      notebookChord(context(command), NotebookChord(Key.J, shift = true)),
    )
  }

  /** `Shift`+`Home`/`End` → `extend-marked-cells-top`/`-bottom` (`tracker.json:575-582`). */
  @Test
  fun shiftHomeAndEnd_inCommand_extendToTheEnds() {
    assertEquals(
      Bound(NotebookChordIntent.ExtendMarked(SelectionReach.TOP)),
      notebookChord(context(command), NotebookChord(Key.MoveHome, shift = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.ExtendMarked(SelectionReach.BOTTOM)),
      notebookChord(context(command), NotebookChord(Key.MoveEnd, shift = true)),
    )
  }

  /** The cursor rows are command-scoped: every one of them stays the field's own key in edit mode. */
  @Test
  fun theCursorRows_doNotFireInEditMode() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.DirectionUp)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.DirectionDown)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.DirectionLeft)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.DirectionRight)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.J)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.K)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.J, shift = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.MoveHome, shift = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.MoveEnd, shift = true)))
  }

  /** Unshifted `Home`/`End` are bound nowhere upstream - the field's caret keeps them. */
  @Test
  fun bareHomeAndEnd_resolveToNothing() {
    assertEquals(Unbound, notebookChord(context(command), NotebookChord(Key.MoveHome)))
    assertEquals(Unbound, notebookChord(context(command), NotebookChord(Key.MoveEnd)))
  }

  /**
   * `Escape` on the editing selector → `notebook:enter-command-mode` (`tracker.json:457-462`).
   * JupyterLab's implementation "just unfocuses the editor"; here unfocusing is a focus *move*,
   * so the intent reaches the dispatch site rather than a state transform.
   */
  @Test
  fun escape_inEdit_entersCommandMode() {
    assertEquals(
      Bound(NotebookChordIntent.EnterCommandMode),
      notebookChord(context(edit), NotebookChord(Key.Escape)),
    )
  }

  /** `Ctrl M` is the alternate binding of the same command (`tracker.json:464-469`). */
  @Test
  fun ctrlM_inEdit_entersCommandMode() {
    assertEquals(
      Bound(NotebookChordIntent.EnterCommandMode),
      notebookChord(context(edit), NotebookChord(Key.M, ctrl = true)),
    )
  }

  /** The mode-crossing rows are mode-scoped: neither fires from the wrong side. */
  @Test
  fun theModeRows_doNotFireFromTheWrongMode() {
    assertEquals(Unbound, notebookChord(context(command), NotebookChord(Key.Escape)))
    assertEquals(Unbound, notebookChord(context(command), NotebookChord(Key.M, ctrl = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Enter)))
  }

  /** An `Escape` carrying a modifier is not the binding. */
  @Test
  fun escape_withAModifierHeld_doesNotMatch() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Escape, shift = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Escape, ctrl = true)))
  }

  /** A `Ctrl M` carrying another modifier is not the binding either. */
  @Test
  fun ctrlM_withShiftOrAltHeld_doesNotMatch() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.M, ctrl = true, shift = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.M, ctrl = true, alt = true)))
  }

  /** The modifier flag alone is the match - the run column does not care what the others hold. */
  @Test
  fun ctrlEnter_withAltOrShiftHeld_doesNotMatch() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Enter, ctrl = true, shift = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Enter, ctrl = true, alt = true)))
  }

  @Test
  fun shiftEnter_withCtrlHeld_doesNotMatch() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Enter, shift = true, ctrl = true)))
  }

  @Test
  fun altEnter_withCtrlHeld_doesNotMatch() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Enter, alt = true, ctrl = true)))
  }

  /** An unmodified Enter on a field is the editor's own newline; the keymap must not claim it. */
  @Test
  fun bareEnter_staysANewline() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Enter)))
  }

  /** The run chords are editor-scoped rows; in command mode `Shift Enter` fires nowhere upstream. */
  @Test
  fun shiftEnter_doesNotFireInCommandMode() {
    assertEquals(Unbound, notebookChord(context(command), NotebookChord(Key.Enter, shift = true)))
  }

  /**
   * `Ctrl Enter`/`Alt Enter` on the focused selector → `notebook:run` / `notebook:run-and-insert`
   * in *command* mode too (`tracker.json`'s `run-cell`/`run-cell-and-insert-below` command rows) -
   * a frame holds focus there, and running it needs no caret.
   */
  @Test
  fun ctrlAndAltEnter_inCommand_run() {
    assertEquals(
      Bound(NotebookChordIntent.RunSelected),
      notebookChord(context(command), NotebookChord(Key.Enter, ctrl = true)),
    )
    assertEquals(
      Bound(NotebookChordIntent.RunAndInsert),
      notebookChord(context(command), NotebookChord(Key.Enter, alt = true)),
    )
  }

  /**
   * Keyboards split Enter into two keycodes, and the table keys on the *key* rather than the
   * hardware that sent it, so both resolves (the normalisation the Compose wrapper performs -
   * `KeyEvent.chord` maps `KEYCODE_NUMPAD_ENTER` onto `Key.Enter`).
   */
  @Test
  fun numpadEnter_resolvesAsEnter() {
    assertEquals(
      Bound(NotebookChordIntent.RunSelected),
      notebookChord(context(edit), NotebookChord(Key.NumPadEnter, ctrl = true)),
    )
  }

  /** Modifier chords that mean nothing fall through to the editor. */
  @Test
  fun unmatchedChords_resolveToNothing() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.Enter, meta = true)))
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.A, ctrl = true, shift = true)))
  }

  /**
   * Lumino's `matchKeystroke` partial match: the first stroke of a two-key binding resolves to
   * [Pending], which is what keeps a lone `D` out of the document while the second half of
   * `notebook:delete-cell`'s `["D","D"]` (`tracker.json:390-395`) is still open.
   */
  @Test
  fun theFirstHalfOfASequence_resolvesPending() {
    assertEquals(Pending, notebookChord(context(command), NotebookChord(Key.D)))
  }

  /** `[pending, stroke]` matching a row's `keys` is what completes the sequence. */
  @Test
  fun theSecondHalfOfASequence_completesIt() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Delete)),
      notebookChord(context(command), NotebookChord(Key.D), pending = NotebookChord(Key.D)),
    )
  }

  /**
   * A stroke that completes nothing resets the sequence and resolves fresh - Lumino drops the
   * open keystrokes on a miss and tries the stroke alone, so `D` then `X` is a cut, not a
   * delete-and-nothing.
   */
  @Test
  fun aNonCompletingStroke_resetsAndResolvesAlone() {
    assertEquals(
      Bound(NotebookChordIntent.ApplyCellAction(CellAction.Cut)),
      notebookChord(context(command), NotebookChord(Key.X), pending = NotebookChord(Key.D)),
    )
    assertEquals(
      Unbound,
      notebookChord(context(command), NotebookChord(Key.W), pending = NotebookChord(Key.D)),
    )
  }

  /** `D` opens no sequence in edit mode - the letter rows are command-scoped. */
  @Test
  fun sequences_openOnlyInTheirMode() {
    assertEquals(Unbound, notebookChord(context(edit), NotebookChord(Key.D)))
  }
}
