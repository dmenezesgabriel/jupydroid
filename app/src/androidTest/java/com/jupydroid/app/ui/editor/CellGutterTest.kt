package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
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
  private fun EditorCellFor(cell: Cell) {
    JupyterDroidTheme {
      EditorCell(
        cell = cell,
        outputs = emptyList(),
        executionCount = null,
        sourceRevision = 0,
        execution = null,
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
