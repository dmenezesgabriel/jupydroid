package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the menu says it will do, once it can be asked to do it to several cells.
 *
 * The reference pluralises with `trans._n` and only where the count changes the noun -
 * `'Delete Cell'`/`'Delete Cells'`, `'Move Cell Up'`/`'Move Cells Up'`, `'Cut Cell'`/`'Cut Cells'`
 * (`packages/notebook-extension/src/index.ts:3480`, `:3733`, `:4297`) - while `'Clear Cell Output'`
 * and `'Collapse Selected Code'` have one form each (`:3365`, `:4741`). Both halves are copied.
 */
class CellActionItemsTest {
  @Test
  fun oneCellReadsExactlyAsItAlwaysHas() {
    val labels = itemsFor(count = 1)

    assertTrue(labels.containsAll(listOf("Move up", "Duplicate", "Cut", "Copy", "Delete")))
  }

  @Test
  fun aRunPluralisesTheRowsTheReferencePluralises() {
    val labels = itemsFor(count = 3)

    assertTrue(
      labels.containsAll(
        listOf("Move cells up", "Move cells down", "Duplicate cells", "Cut cells", "Copy cells", "Delete cells"),
      ),
    )
    assertFalse(labels.contains("Delete"))
  }

  /** One form each there, so one form each here (`index.ts:3365`, `:4741`). */
  @Test
  fun aRunLeavesTheRowsTheReferenceLeaves() {
    val labels = itemsFor(count = 3)

    assertTrue(labels.containsAll(listOf("Clear outputs", COLLAPSE_CODE, "Split cell", "Insert code below")))
  }

  @Test
  fun aRunChangesTheTypeOfAllOfThem() {
    assertTrue(itemsFor(count = 3).contains("Change cells to markdown"))
    assertTrue(itemsFor(count = 1).contains(CHANGE_TO_MARKDOWN))
  }

  /**
   * **Two rows, not one**: there are three cell types and the reference has a command for each -
   * `notebook:change-cell-to-{code,markdown,raw}` (`packages/notebook-extension/src/index.ts:212-214`).
   * A single "change type" row could only ever name one of the two a cell is not, so the other
   * would be unreachable from the menu entirely.
   */
  @Test
  fun everyCellOffersTheTwoTypesItIsNot() {
    assertEquals(listOf(CHANGE_TO_MARKDOWN, CHANGE_TO_RAW), changeRowsFor(CellKind.CODE))
    assertEquals(listOf(CHANGE_TO_CODE, CHANGE_TO_RAW), changeRowsFor(CellKind.MARKDOWN))
    assertEquals(listOf(CHANGE_TO_CODE, CHANGE_TO_MARKDOWN), changeRowsFor(CellKind.RAW))
  }

  /** The rows carry the action, not only the words. */
  @Test
  fun theRawRowAsksForARawCell() {
    val items = cellActionItems(Cell("c1", CellKind.CODE, "x = 1"), canPaste = false, selecting = false)

    assertEquals(CellAction.ChangeKind(CellKind.RAW), items.single { it.first == CHANGE_TO_RAW }.second)
  }

  /** Pluralised the way every other type row is, and by the same rule. */
  @Test
  fun aRunChangesAllOfThemToRawToo() {
    assertTrue(itemsFor(count = 3).contains("Change cells to raw"))
  }

  /**
   * The row that stands for the six heading levels sits with the two type rows, because it *is* a
   * third one - `setMarkdownHeader` is `changeCellType(…, 'markdown', { headingLevel })`
   * (`packages/notebook/src/actions.tsx:2197-2214`) - and it carries a request for the picker
   * rather than an edit.
   */
  @Test
  fun everyCellAlsoOffersTheHeadingLevels() {
    val items = cellActionItems(Cell("c1", CellKind.CODE, "x = 1"), canPaste = false, selecting = false)
    val labels = items.map { it.first }

    assertEquals(CellAction.ChooseHeadingLevel, items.single { it.first == CHANGE_TO_HEADING }.second)
    assertEquals(labels.indexOf(CHANGE_TO_RAW) + 1, labels.indexOf(CHANGE_TO_HEADING))
  }

  /**
   * The two `insert-heading-*` rows are in the adding group, because that is what they do - they
   * add a cell rather than convert this one.
   *
   * **And "below" is spelled differently on purpose.** `notebook:insert-cell-below` puts a cell
   * after this one; `notebook:insert-heading-below` puts a heading at the end of the *section*
   * (`packages/notebook/src/actions.tsx:1208-1231`), so two rows both reading "below" would promise
   * the same place and mean different ones.
   */
  @Test
  fun theTwoHeadingInsertsSitWithTheOtherInserts() {
    val items = cellActionItems(Cell("c1", CellKind.CODE, "x = 1"), canPaste = false, selecting = false)
    val labels = items.map { it.first }

    assertEquals(CellAction.InsertHeading(SectionReach.ABOVE), items.single { it.first == INSERT_HEADING_ABOVE }.second)
    assertEquals(CellAction.InsertHeading(SectionReach.BELOW), items.single { it.first == INSERT_HEADING_BELOW }.second)
    assertEquals(labels.indexOf("Insert markdown below") + 1, labels.indexOf(INSERT_HEADING_ABOVE))
    assertEquals(labels.indexOf(INSERT_HEADING_ABOVE) + 1, labels.indexOf(INSERT_HEADING_BELOW))
    assertTrue("only one row may say \"below\" and mean the next cell", INSERT_HEADING_BELOW.endsWith("section"))
  }

  /** One form whatever the count: the reference's six labels have no plural (`index.ts:4614-4740`). */
  @Test
  fun theHeadingRowReadsTheSameForARun() {
    assertTrue(itemsFor(count = 3).contains(CHANGE_TO_HEADING))
  }

  /** Six levels, named as the reference names them, each carrying its own level. */
  @Test
  fun thePickerOffersTheSixMarkdownHas() {
    assertEquals(
      listOf("Heading 1", "Heading 2", "Heading 3", "Heading 4", "Heading 5", "Heading 6"),
      headingLevelItems().map { it.first },
    )
    assertEquals(CellAction.ChangeToHeading(4), headingLevelItems()[3].second)
  }

  /** The two *type* rows, which is what [everyCellOffersTheTwoTypesItIsNot] is about. */
  private fun changeRowsFor(kind: CellKind) =
    cellActionItems(Cell("c1", kind, "x = 1"), canPaste = false, selecting = false)
      .map { it.first }
      .filter { it.startsWith("Change to") && it != CHANGE_TO_HEADING }

  /**
   * **The two directional merges become one row, because the direction stops applying.**
   * `mergeCells` consults `mergeAbove` only inside `if (toMerge.length === 1)`
   * (`packages/notebook/src/actions.tsx:390-425`), so with a run both rows would do the same
   * thing - and two rows that do one thing is a menu that lies about one of them.
   *
   * The replacement is not invented either: it is the reference's own third merge command,
   * `notebook:merge-cells`, labelled *"Merge Selected Cells"* (`index.ts:3953`).
   */
  @Test
  fun aRunOffersOneMergeInsteadOfTwo() {
    val labels = itemsFor(count = 3)

    assertEquals(listOf(MERGE_SELECTED), labels.filter { it.startsWith("Merge") })
  }

  @Test
  fun oneCellStillOffersBothDirections() {
    val labels = itemsFor(count = 1)

    assertEquals(listOf("Merge with above", "Merge with below"), labels.filter { it.startsWith("Merge") })
  }

  private fun itemsFor(count: Int) = cellActionItems(
    Cell("c1", CellKind.CODE, "x = 1"),
    canPaste = false,
    selecting = count > 1,
    count = count,
  ).map { it.first }

  // --- the session-ask rows: "Symbol info", "Go to definition", "Format cell" ---

  /**
   * The three asks as menu rows. They lead under the selection row, jump first, for the reason VS Code's editor context
   * menu leads its navigation group with `editor.action.goToDefinition`
   * (`editorContextMenu.ts`): all three ask about the code where every row below them edits the
   * notebook, and the jump is the stronger promise. "Format cell" sits last of the three, which
   * is also the reference's order - `notebook.formatCell` is registered in the `1_modification`
   * group, under the navigation asks.
   */
  @Test
  fun aCodeCellWithASession_offersTheInspectRows() {
    val items = cellActionItems(
      Cell("c1", CellKind.CODE, "x = 1"),
      canPaste = false,
      selecting = false,
      inspection = true,
    )
    val labels = items.map { it.first }

    assertEquals(CellAction.GoToDefinition, items.single { it.first == GO_TO_DEFINITION }.second)
    assertEquals(CellAction.SymbolInfo, items.single { it.first == SYMBOL_INFO }.second)
    assertEquals(CellAction.RenameSymbol, items.single { it.first == RENAME_SYMBOL }.second)
    assertEquals(CellAction.FormatCell, items.single { it.first == FORMAT_CELL }.second)
    assertTrue("Delete stays the last row", labels.last() == "Delete")
    assertEquals("the jump leads the menu, under Select cells", 1, labels.indexOf(GO_TO_DEFINITION))
    assertEquals("the hover ask is right under it", 2, labels.indexOf(SYMBOL_INFO))
    assertEquals("rename leads the modification rows", 3, labels.indexOf(RENAME_SYMBOL))
    assertEquals("format is the last ask row", 4, labels.indexOf(FORMAT_CELL))
  }

  /**
   * Offered only while there is a session to answer - the same rule [canPaste] already keeps:
   * `cellActionItems`'s own words are that an item that silently does nothing teaches the user
   * the menu lies, and an ask with no language server behind it could only ever do nothing.
   */
  @Test
  fun noSession_offersNoInspectRows() {
    val labels = cellActionItems(
      Cell("c1", CellKind.CODE, "x = 1"),
      canPaste = false,
      selecting = false,
    ).map { it.first }

    assertFalse(labels.contains(GO_TO_DEFINITION))
    assertFalse(labels.contains(SYMBOL_INFO))
    assertFalse(labels.contains(FORMAT_CELL))
  }

  /** The virtual document maps code cells only - a markdown cell's ask could never resolve. */
  @Test
  fun aMarkdownCell_offersNoInspectRows() {
    val labels = cellActionItems(
      Cell("c1", CellKind.MARKDOWN, "# notes"),
      canPaste = false,
      selecting = false,
      inspection = true,
    ).map { it.first }

    assertFalse(labels.contains(GO_TO_DEFINITION))
    assertFalse(labels.contains(SYMBOL_INFO))
    assertFalse(labels.contains(FORMAT_CELL))
  }

  /**
   * "Rename symbol" sits before "Format cell" - VS Code's own `1_modification` order:
   * `RenameAction` at 1.1, the format command at 1.3 (`rename.ts`'s MenuGroup registration).
   * And like its neighbours it is offered only with a session to answer it.
   */
  @Test
  fun aSessionedCodeCell_offersRenameBeforeFormat() {
    val labels = cellActionItems(
      Cell("c1", CellKind.CODE, "x = 1"),
      canPaste = false,
      selecting = false,
      inspection = true,
    ).map { it.first }

    val rename = labels.indexOf(RENAME_SYMBOL)
    assertTrue(rename >= 0 && rename < labels.indexOf(FORMAT_CELL))
  }

  @Test
  fun noSession_offersNoRenameEither() {
    val labels = cellActionItems(
      Cell("c1", CellKind.CODE, "x = 1"),
      canPaste = false,
      selecting = false,
    ).map { it.first }

    assertFalse(labels.contains(RENAME_SYMBOL))
  }

  // --- notebook:enable/disable/toggle-output-scrolling -----------------------------

  /**
   * One fixed label whatever the state, which is the reference's own choice for this exact
   * surface: `toggleOutputScrolling`'s label reads *"Enable Scrolling for Outputs"* whenever
   * `args['isMenu']` is set, and only *"Toggle Scrolling for Outputs"* off a menu
   * (`packages/notebook-extension/src/index.ts:4963-4967`) - `tracker.json:328-335` registers the
   * notebook's context menu row with `args: {isMenu: true}`. So a tap on this row can *disable*
   * scrolling on an already-scrolled cell even though the row still reads "Enable"; that is
   * literal parity; it is not this app inventing a flip-with-state label the way the three folds
   * beside it do.
   */
  @Test
  fun aCodeCellOffersToEnableOutputScrolling() {
    val cell = Cell("c1", CellKind.CODE, "x = 1", outputsScrolled = true)
    val items = cellActionItems(cell, canPaste = false, selecting = false)

    assertEquals(CellAction.ToggleOutputsScrolled, items.single { it.first == ENABLE_OUTPUT_SCROLLING }.second)
  }

  /** Code cells only, like the two collapses beside it - a markdown cell has no output at all. */
  @Test
  fun aMarkdownCellOffersNoOutputScrollingRow() {
    val cell = Cell("c1", CellKind.MARKDOWN, "# h")
    val labels = cellActionItems(cell, canPaste = false, selecting = false).map { it.first }

    assertFalse(labels.contains(ENABLE_OUTPUT_SCROLLING))
  }
}
