package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which cells each run command means, against the reference that defines it.
 *
 * Every case names the file and line it comes from, as [CellRangeSelectionTest] does. The pair
 * that a first guess gets wrong is [ABOVE] and [BELOW]: one excludes the head and the other
 * includes it, and nothing about the two names says so.
 */
class CellRunRangeTest {
  /**
   * `runAll` passes `notebook.widgets` - every cell, markdown included
   * (`packages/notebook/src/actions.tsx:940-968`). Which of them reaches a kernel is
   * [NotebookCellRuns]'s question, not this one's.
   */
  @Test
  fun all_isEveryCellWhateverIsSelected() {
    val state = stateOf("a", "b", "c").withCellSelected("b")

    assertEquals(listOf("a", "b", "c"), state.cellsToRun(RunRange.ALL).ids())
  }

  /**
   * **And with nothing selected at all**, which is not what the reference does - `runAll` bails on
   * a notebook with no `activeCell` (`actions.tsx:946`). Ours cannot: Run All is a button on the
   * notebook's own bar and is reachable before any cell has been touched, and refusing there would
   * be a control that lights up and does nothing. `.maestro/step4b-run-all-and-stop.yaml` taps it
   * on a freshly opened notebook.
   */
  @Test
  fun all_isEveryCellWithNothingSelected() {
    assertEquals(listOf("a", "b"), stateOf("a", "b").cellsToRun(RunRange.ALL).ids())
  }

  /**
   * `runAllAbove` slices `(0, activeCellIndex)` - **exclusive** of the head
   * (`packages/notebook/src/actions.tsx:1005-1045`).
   */
  @Test
  fun above_stopsShortOfTheHead() {
    val state = stateOf("a", "b", "c", "d").withCellSelected("c")

    assertEquals(listOf("a", "b"), state.cellsToRun(RunRange.ABOVE).ids())
  }

  /**
   * `runAllAbove` returns early on `activeCellIndex < 1` (`actions.tsx:1011`), which is also why
   * `notebook:run-all-above` is disabled at index 0 (`notebook-extension/src/index.ts:3082-3089`).
   */
  @Test
  fun above_isEmptyOnTheFirstCell() {
    val state = stateOf("a", "b").withCellSelected("a")

    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.ABOVE).ids())
  }

  /**
   * `runAllBelow` slices `(activeCellIndex)` - **inclusive** of the head, which is what its label
   * says out loud: *"Run Selected Cell and All Below"* (`actions.tsx:1047-1090`, `index.ts:3098`).
   */
  @Test
  fun below_startsAtTheHead() {
    val state = stateOf("a", "b", "c", "d").withCellSelected("c")

    assertEquals(listOf("c", "d"), state.cellsToRun(RunRange.BELOW).ids())
  }

  @Test
  fun below_isTheHeadAloneOnTheLastCell() {
    val state = stateOf("a", "b").withCellSelected("b")

    assertEquals(listOf("b"), state.cellsToRun(RunRange.BELOW).ids())
  }

  /**
   * `restart-and-run-to-selected` slices `(0, activeCellIndex + 1)` - **inclusive**, and so the
   * one range that is neither of the two above (`notebook-extension/src/index.ts:3273`).
   */
  @Test
  fun upToSelected_includesTheHead() {
    val state = stateOf("a", "b", "c").withCellSelected("b")

    assertEquals(listOf("a", "b"), state.cellsToRun(RunRange.UP_TO_SELECTED).ids())
  }

  @Test
  fun upToSelected_isTheFirstCellAloneOnTheFirstCell() {
    val state = stateOf("a", "b").withCellSelected("a")

    assertEquals(listOf("a"), state.cellsToRun(RunRange.UP_TO_SELECTED).ids())
  }

  /**
   * `Private.runSelected` filters `isSelectedOrActive` (`actions.tsx:3009-3040`), which is
   * [selectedCellIds] - so a marked run of three is three cells and an unmarked notebook is the
   * head alone, exactly as every cell command already behaves.
   */
  @Test
  fun marked_isTheWholeRun() {
    val state = stateOf("a", "b", "c", "d").withSelectionStartedAt("b").withSelectionExtendedTo("c")

    assertEquals(listOf("b", "c"), state.cellsToRun(RunRange.MARKED).ids())
  }

  @Test
  fun marked_isTheHeadAloneWithNothingMarked() {
    val state = stateOf("a", "b", "c").withCellSelected("b")

    assertEquals(listOf("b"), state.cellsToRun(RunRange.MARKED).ids())
  }

  /**
   * No head, no range - and every one of the four head-relative ranges says so, rather than one
   * of them quietly meaning "the whole notebook". The reference bails on a missing `activeCell` in
   * all four (`actions.tsx:1008`, `:1053`, `index.ts:3269`).
   */
  @Test
  fun everyHeadRelativeRange_isEmptyWithNoSelectedCell() {
    val state = stateOf("a", "b")

    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.ABOVE).ids())
    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.BELOW).ids())
    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.UP_TO_SELECTED).ids())
    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.MARKED).ids())
  }

  /** A selected cell that has since gone is no head at all, which is [selectedCellIds]' rule too. */
  @Test
  fun everyHeadRelativeRange_isEmptyWhenTheSelectedCellIsGone() {
    val state = stateOf("a", "b").copy(selectedCellId = "missing")

    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.ABOVE).ids())
    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.BELOW).ids())
    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.UP_TO_SELECTED).ids())
    assertEquals(emptyList<String>(), state.cellsToRun(RunRange.MARKED).ids())
  }

  /**
   * `notebook:run-all-above` is disabled at index 0
   * (`packages/notebook-extension/src/index.ts:3082-3089`), which is the same fact as its range
   * being empty there - stated separately because the bar has to grey the row *before* anyone taps
   * it.
   */
  @Test
  fun runAllAbove_isOfferedOnlyBelowTheFirstCell() {
    val cells = stateOf("a", "b", "c")

    assertFalse(cells.withCellSelected("a").canRunAllAbove)
    assertTrue(cells.withCellSelected("b").canRunAllAbove)
    assertTrue(cells.withCellSelected("c").canRunAllAbove)
  }

  @Test
  fun runAllAbove_isNotOfferedWithNothingSelected() {
    assertFalse(stateOf("a", "b").canRunAllAbove)
  }

  /**
   * `notebook:run-all-below` is disabled on the **last** cell (`index.ts:3113-3119`), even though
   * the range there is non-empty - with nothing below it, *"Run Selected Cell and All Below"* is
   * the cell's own Run button under a longer name.
   */
  @Test
  fun runSelectedAndBelow_isNotOfferedOnTheLastCell() {
    val cells = stateOf("a", "b", "c")

    assertTrue(cells.withCellSelected("a").canRunSelectedAndBelow)
    assertTrue(cells.withCellSelected("b").canRunSelectedAndBelow)
    assertFalse(cells.withCellSelected("c").canRunSelectedAndBelow)
  }

  /**
   * **And the one-cell notebook is the exception the reference spells out**: `widgets.length === 1`
   * enables it regardless (`index.ts:3115-3116`). That cell is both the first and the last, so the
   * rule above would otherwise refuse a notebook where the command is perfectly meaningful.
   */
  @Test
  fun runSelectedAndBelow_isOfferedOnTheOnlyCellOfAOneCellNotebook() {
    assertTrue(stateOf("a").withCellSelected("a").canRunSelectedAndBelow)
  }

  @Test
  fun runSelectedAndBelow_isNotOfferedWithNothingSelected() {
    assertFalse(stateOf("a", "b").canRunSelectedAndBelow)
  }

  /**
   * **Every one of these four commands deselects**, and it is the same line in each:
   * `runAll` (`actions.tsx:966`), `runAllAbove` (`:1032`), `runAllBelow` (`:1077`) and
   * `runSelected` (`:3031`) all end on `notebook.deselectAll()`.
   */
  @Test
  fun startingARun_dropsTheMarkedRun() {
    val marked = stateOf("a", "b", "c").withSelectionStartedAt("a").withSelectionExtendedTo("b")

    RunRange.entries.forEach { range ->
      assertFalse("$range should deselect", marked.withRunStarted(range).selecting)
    }
  }

  /**
   * `runSelected` moves the head to the **last** selected cell before deselecting - it tracks
   * `lastIndex` through the filter and assigns it (`actions.tsx:3020-3030`). So the caret ends up
   * after the work rather than back at the top of it.
   */
  @Test
  fun startingAMarkedRun_leavesTheHeadOnItsLastCell() {
    val marked = stateOf("a", "b", "c", "d").withSelectionStartedAt("b").withSelectionExtendedTo("c")

    assertEquals("c", marked.withRunStarted(RunRange.MARKED).selectedCellId)
  }

  /** `runAll` sets `activeCellIndex = lastIndex` (`actions.tsx:964`), which clamps to the last cell. */
  @Test
  fun startingARunOfEverything_leavesTheHeadOnTheLastCell() {
    val state = stateOf("a", "b", "c").withCellSelected("a")

    assertEquals("c", state.withRunStarted(RunRange.ALL).selectedCellId)
  }

  /** `runAllBelow` does the same (`actions.tsx:1075`), and the last cell it runs *is* the last cell. */
  @Test
  fun startingARunBelow_leavesTheHeadOnTheLastCell() {
    val state = stateOf("a", "b", "c").withCellSelected("b")

    assertEquals("c", state.withRunStarted(RunRange.BELOW).selectedCellId)
  }

  /**
   * **And `run-all-above` is the exception**, in the reference's own words: *"The currently active
   * cell will remain selected"* (`actions.tsx:999`). It deselects and moves nothing, so the head
   * stays where the user put it rather than jumping to the cell above it.
   */
  @Test
  fun startingARunAbove_leavesTheHeadWhereItWas() {
    val state = stateOf("a", "b", "c").withCellSelected("b")

    assertEquals("b", state.withRunStarted(RunRange.ABOVE).selectedCellId)
  }

  /** Nothing to run, nothing to move to - the head stays rather than being cleared. */
  @Test
  fun startingAnEmptyRun_leavesTheHeadWhereItWas() {
    val state = stateOf("a", "b").withCellSelected("a")

    assertEquals("a", state.withRunStarted(RunRange.ABOVE).selectedCellId)
  }

  private fun List<Cell>.ids() = map { it.id }

  private fun stateOf(vararg ids: String) = EditorUiState(
    cells = ids.map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  )
}
