package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P2v-1: the run gutter. A fixed column left of the editor holding Run / Stop / spinner,
 * top-aligned with the editor's first line, where the header strip held them until now
 * (`media/notebook.css:228-234`, `notebookOptions.ts:118-136`). Drawn on **all three kinds** -
 * code's controls, markdown's render-play, raw empty so every editor shares one x
 * (`docs/DECISIONS.md`, "The run gutter is re-taken").
 *
 * The width asserts nothing about itself here beyond the geometry the gate names: the Run
 * control's bounds are left of the editor's and hang from its top, at both widths this app is
 * driven at. `CELL_RUN_GUTTER`'s own number is pinned by the touch-target suite.
 */
@RunWith(AndroidJUnit4::class)
class CellGutterTest {
  @get:Rule val compose = createComposeRule()

  /** The emulator's own window, and the narrowest this app is driven at. */
  @Test
  fun theRunControlIsLeftOfTheEditorAndSharesItsTopYAt320dp() {
    assertTheRunControlSitsLeftOfAndAboveTheEditor(COMPACT)
  }

  /** The tablet's portrait width. */
  @Test
  fun theRunControlIsLeftOfTheEditorAndSharesItsTopYAt1280dp() {
    assertTheRunControlSitsLeftOfAndAboveTheEditor(WIDE)
  }

  /** Markdown runs to render - a real thing to do - so its play lives in the gutter too. */
  @Test
  fun aMarkdownCellsPlayIsInTheGutter() {
    compose.setContent { EditorCellFor(Cell("m1", CellKind.MARKDOWN, "# hi")) }

    compose.onNodeWithContentDescription(RUN_BUTTON).assertExists()
    compose.onNodeWithText("# hi").assertExists()
  }

  /**
   * Raw runs nothing, so its gutter slot is empty - but the column itself is there, which is the
   * point: a raw editor starting 48dp left of every neighbour would be the ragged edge P2n spent
   * itself removing.
   */
  @Test
  fun aRawCellKeepsTheColumnWithoutAControl() {
    compose.setContent {
      Column {
        EditorCellFor(Cell("c1", CellKind.CODE, "x = 1"))
        EditorCellFor(Cell("r1", CellKind.RAW, "\\begin{equation}"))
      }
    }

    val code = compose.onNodeWithText("x = 1").getUnclippedBoundsInRoot()
    val raw = compose.onNodeWithText("\\begin{equation}").getUnclippedBoundsInRoot()
    assertEquals(code.left, raw.left)
  }

  /**
   * Spinner *and* Stop while the kernel works - and beside each other, not stacked: they were
   * siblings of the gutter's Box until P2v-2 caught them drawing on top of one another, which
   * bounds tests on a resting cell could never see.
   */
  @Test
  fun aRunningCellKeepsItsSpinnerBesideStopNotOnIt() {
    compose.setContent {
      EditorCellFor(
        Cell("c1", CellKind.CODE, SOURCE),
        execution = CellExecution(status = ExecutionStatus.RUNNING),
      )
    }

    val spinner = compose.onNodeWithContentDescription("Running").getUnclippedBoundsInRoot()
    val stop = compose.onNodeWithContentDescription("Stop cell").getUnclippedBoundsInRoot()
    assertTrue(
      "the spinner at $spinner overlaps the Stop button at $stop",
      spinner.right <= stop.left,
    )
  }

  /**
   * The reference floors every code editor at `lineCount × lineHeight + padding{top:12,bottom:12}`
   * (`codeCell.ts:195-201`, `cellEditorOptions.ts:170-172`) exactly so a **one-line** cell can
   * still hold both ends of its gutter: the play hung from the editor's top, the `[ ]` pinned to
   * its bottom, and air between them. This is that floor, stated as the inequality it guarantees -
   * measured off the live nodes rather than re-derived, so a taller font raises the floor with it.
   */
  @Test
  fun aOneLineCodeCellStillFitsPlayAndBracketWithAirBetween() {
    compose.setContent { EditorCellFor(Cell("c1", CellKind.CODE, SOURCE)) }

    val run = compose.onNodeWithContentDescription(RUN_BUTTON).getUnclippedBoundsInRoot()
    val count = compose.onNodeWithText("[ ]").getUnclippedBoundsInRoot()
    val editor = compose.onNodeWithTag(CELL_INPUT_AREA).getUnclippedBoundsInRoot()
    assertTrue(
      "editor at $editor is shorter than play $run + gap + bracket $count",
      editor.bottom - editor.top >= run.bottom - run.top + GUTTER_STACK_AIR + count.bottom - count.top,
    )
    assertTrue(
      "only ${count.top - run.bottom} between the play at $run and the bracket at $count",
      count.top - run.bottom >= GUTTER_STACK_AIR,
    )
  }

  /** A collapsed input hides the editor, and with it the label - `_updatePosition` hides the
   * execution order label outright when `isInputCollapsed` (`cellExecution.ts:127-131`). */
  @Test
  fun aCollapsedInputTakesItsBracketWithIt() {
    compose.setContent { EditorCellFor(Cell("c1", CellKind.CODE, SOURCE).copy(sourceHidden = true)) }

    compose.onNodeWithText("[ ]").assertDoesNotExist()
  }

  // --- the execution count at the gutter's foot (P2v-2) ------------------------------------

  /**
   * `[ ]` before a first run, which is the reference's own answer
   * (`view/cellParts/cellExecution.ts:94,133`) - and it lives at the gutter's **foot**, where the
   * header prompt used to say the same thing with JupyterLab's colon.
   */
  @Test
  fun aNeverRunCodeCellReadsAnEmptyBracket() {
    compose.setContent { EditorCellFor(Cell("c1", CellKind.CODE, SOURCE)) }

    compose.onNodeWithText("[ ]").assertExists()
  }

  /**
   * The label sits below the Run control and shares the editor's bottom y - `top =
   * editorHeight - 22 + statusBarHeight` (`cellExecution.ts:139,200`). The field reads higher than
   * the box only by its own border and inner clearance, never by more.
   */
  @Test
  fun theCountSitsBelowTheRunControlAndSharesTheEditorsBottomY() {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(COMPACT)) {
        EditorCellFor(Cell("c1", CellKind.CODE, SOURCE))
      }
    }

    val run = compose.onNodeWithContentDescription(RUN_BUTTON).getUnclippedBoundsInRoot()
    val count = compose.onNodeWithText("[ ]").getUnclippedBoundsInRoot()
    val editor = compose.onNodeWithTag(CELL_INPUT_AREA).getUnclippedBoundsInRoot()
    assertTrue("the count at $count is not below the Run control at $run", count.top >= run.bottom)
    assertTrue(
      "the count ends at $count, off the editor's bottom $editor",
      count.bottom - editor.bottom in 0.dp..EDITOR_CLEARANCE,
    )
  }

  /** Only code reaches a kernel, so only code has a count to show - markdown and raw have none. */
  @Test
  fun markdownAndRawCarryNoCount() {
    compose.setContent {
      Column {
        EditorCellFor(Cell("m1", CellKind.MARKDOWN, "prose"))
        EditorCellFor(Cell("r1", CellKind.RAW, "\\begin{equation}"))
      }
    }

    compose.onNodeWithText("[ ]").assertDoesNotExist()
  }

  /**
   * The outputs read as part of the editor's column, not the frame's: with the gutter now inside
   * the cell (P2v-1), anything below the row that starts at the frame edge sits under the gutter.
   *
   * Measured against the input **area**, not the glyphs in it - the field draws its text inset by
   * border and padding, and an output block that started there would be indented for no reason
   * anyone can see.
   */
  @Test
  fun theOutputsShareTheEditorsLeftEdge() {
    compose.setContent {
      EditorCellFor(
        Cell("c1", CellKind.CODE, SOURCE),
        outputs = listOf(CellOutput.Stream(StreamName.STDOUT, "42")),
      )
    }

    val editor = compose.onNodeWithTag(CELL_INPUT_AREA).getUnclippedBoundsInRoot()
    val output = compose.onNodeWithContentDescription("Cell output").getUnclippedBoundsInRoot()
    assertEquals(editor.left, output.left)
  }

  private fun assertTheRunControlSitsLeftOfAndAboveTheEditor(size: DpSize) {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
        EditorCellFor(Cell("c1", CellKind.CODE, SOURCE))
      }
    }

    val run = compose.onNodeWithContentDescription(RUN_BUTTON).getUnclippedBoundsInRoot()
    val editor = compose.onNodeWithText(SOURCE).getUnclippedBoundsInRoot()
    assertTrue("the run control ends at $run, past the editor starting at $editor", run.right <= editor.left)
    // Both hang from the same row top; the field reads lower only by its own border and inner
    // clearance (1dp + Spacing.sm), never by more - anything past that is misalignment.
    assertTrue(
      "the run control's top ${run.top} does not share the editor's top $editor",
      editor.top - run.top in 0.dp..EDITOR_CLEARANCE,
    )
  }

  /**
   * One EditorCell under the app theme, wired with the inert callbacks a layout test needs. Not
   * a fixture of the ViewModel: everything here is stateless by construction.
   */
  @Composable
  private fun EditorCellFor(
    cell: Cell,
    outputs: List<CellOutput> = emptyList(),
    execution: CellExecution? = null,
  ) {
    JupyterDroidTheme {
      EditorCell(
        cell = cell,
        outputs = outputs,
        executionCount = null,
        sourceRevision = 0,
        execution = execution,
        canPaste = false,
        selected = false,
        mark = null,
        markedCount = 1,
        tinted = false,
        mode = CellMode.COMMAND,
        onRun = {},
        onStop = {},
        onSourceEdited = {},
        completionsAt = { _, _ -> CompletionResult.NONE },
        onCellAction = { _, _ -> },
        onFocusChanged = {},
        onToggleSelected = {},
        markdownRendered = false,
        markdownTakesCaret = false,
        onMarkdownEditRequested = {},
        hiddenBelow = 0,
        lineNumbersShown = false,
        headingPrefix = "",
        search = CellSearchHighlight.NONE,
        outputSearch = emptyList(),
        autoClosingBrackets = true,
        cellInput = FocusedCellInput(),
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  private companion object {
    const val RUN_BUTTON = "Run cell"
    const val SOURCE = "print(42)"

    /** The emulator's own window, and the narrowest this app is driven at. */
    val COMPACT = DpSize(320.dp, 640.dp)

    /** The tablet's portrait width. */
    val WIDE = DpSize(1280.dp, 800.dp)

    /** The field's own border and inner clearance, the most the text may sit below the control. */
    val EDITOR_CLEARANCE = 14.dp
  }
}
