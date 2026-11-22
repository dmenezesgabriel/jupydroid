package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The overflow the notebook's bar opens: the View menu, the table of contents and the export
 * picker.
 *
 * **Split out of [EditorTopBarTest] when that file passed 500 lines**, and along a real seam
 * rather than at a line count: the bar carries a document's identity and its one-tap actions, and
 * this is what its overflow holds. The reference draws the same line - its notebook toolbar
 * carries the document's own commands (`packages/notebook-extension/schema/panel.json`) while
 * View, the table of contents and export are menu entries elsewhere.
 *
 * It drives [EditorTopBar] rather than [NotebookActionsMenu] on its own, because what these
 * assert is that a row is *reachable*: the menu opens from a button on the bar, and a test that
 * composed the menu directly would still pass with that button gone.
 */
@RunWith(AndroidJUnit4::class)
class NotebookActionsMenuTest {
  @get:Rule val compose = createComposeRule()

  /**
   * Its own overflow rather than [NotebookBar]'s, so what is asserted here is that there is a
   * second way in at all - the question no JVM test answers.
   */
  @Test
  fun theNotebookMenuOffersBothHeadingCommands() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(COLLAPSE_ALL_HEADINGS).assertIsDisplayed()
    compose.onNodeWithText(EXPAND_ALL_HEADINGS).assertIsDisplayed()
  }

  @Test
  fun collapseAllAsksForTheCollapse() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(COLLAPSE_ALL_HEADINGS).performClick()

    assertEquals(listOf(true), headingsSetTo)
  }

  /** The opposite row carries the opposite argument, which is the whole of the pair's difference. */
  @Test
  fun expandAllAsksForTheExpand() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(EXPAND_ALL_HEADINGS).performClick()

    assertEquals(listOf(false), headingsSetTo)
  }

  @Test
  fun theNotebookMenuOffersTheTableOfContents() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(SHOW_TABLE_OF_CONTENTS).assertIsDisplayed()
  }

  /**
   * The row opens [TableOfContentsPanel] rather than firing a callback directly - a picker inside
   * the picker, the same two-step [CellActions]' own heading-level surface already establishes -
   * and picking a heading there is what reports the cell.
   */
  @Test
  fun theRowOpensThePanelAndAPickReportsTheCell() {
    showTopBar(tableOfContentsHeadings = listOf(TocHeading("h1", 1, "Chapter one")))

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(SHOW_TABLE_OF_CONTENTS).performClick()
    compose.onNodeWithContentDescription("Table of contents Chapter one").performClick()

    assertEquals(listOf("h1"), tableOfContentsSelections)
  }

  /**
   * With a shell panel to activate, the row asks for it rather than opening an overlay - the panel
   * is already drawing the list, so a second copy over the top of it would be two answers to one
   * question. `toc:show-panel` is exactly that there: `labShell.activateById(toc.id)` and nothing
   * else (`packages/toc-extension/src/index.ts:161-172`).
   */
  @Test
  fun withASidePanelTheRowActivatesItAndOpensNoOverlay() {
    showTopBar(
      tableOfContentsHeadings = listOf(TocHeading("h1", 1, "Chapter one")),
      onShowTableOfContentsPanel = { tableOfContentsPanelActivations++ },
    )

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(SHOW_TABLE_OF_CONTENTS).performClick()

    compose.runOnIdle { assertEquals(1, tableOfContentsPanelActivations) }
    compose.onNodeWithContentDescription("Table of contents Chapter one").assertDoesNotExist()
  }

  /**
   * **One label, not two**, which departs from [SHOW_LINE_NUMBERS]' convention because the
   * reference's command departs from a toggle: `toc:show-panel` activates the panel and declares no
   * `isToggled`, so there is no opposite state for the row to name.
   */
  @Test
  fun theRowReadsTheSameWhicheverSurfaceItWouldOpen() {
    showTopBar(onShowTableOfContentsPanel = { tableOfContentsPanelActivations++ })

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(SHOW_TABLE_OF_CONTENTS).assertIsDisplayed()
  }

  @Test
  fun theDebuggerRowIsAbsentWithNoSidePanel() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(SHOW_DEBUGGER_PANEL).assertDoesNotExist()
  }

  /**
   * With no side panel to activate, [DebuggerControlBar]'s own toggle is where a stop is seen
   * instead - reachable only while the kernel is actually paused, which this menu has no
   * business knowing about at all.
   */
  @Test
  fun withASidePanelTheDebuggerRowActivatesItAndOpensNoOverlay() {
    showTopBar(onShowDebuggerPanel = { debuggerPanelActivations++ })

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(SHOW_DEBUGGER_PANEL).performClick()

    compose.runOnIdle { assertEquals(1, debuggerPanelActivations) }
    compose.onNodeWithText("Not stopped").assertDoesNotExist()
  }

  @Test
  fun theNotebookMenuOffersToExport() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(EXPORT_NOTEBOOK).assertIsDisplayed()
  }

  /**
   * The Space is started on demand, so nothing is asked of it until the picker is opened - a menu
   * that booted a runtime to populate itself would cost seconds on a tap.
   */
  @Test
  fun openingTheMenuAsksTheSpaceNothing() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    assertEquals(0, exportPickerOpens)
  }

  @Test
  fun choosingExportAsksTheSpaceWhatItCanConvertTo() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(EXPORT_NOTEBOOK).performClick()

    assertEquals(1, exportPickerOpens)
  }

  /**
   * Four rows, and every one of them was measured converting on the tablet
   * - see [OFFERED_EXPORT_FORMATS].
   */
  @Test
  fun theExportPickerOffersTheFormatsThisDeviceCanConvert() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(EXPORT_NOTEBOOK).performClick()

    listOf("HTML", "Markdown", "Executable Script", "Reveal.js Slides")
      .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
  }

  @Test
  fun pickingAFormatReportsTheIdRatherThanTheLabel() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(EXPORT_NOTEBOOK).performClick()
    compose.onNodeWithText("Executable Script").performClick()

    // `python`, not `script`: ScriptExporter needs metadata.language_info and this app writes none.
    assertEquals(listOf("python"), exportedAs)
  }

  /** The picker is the one menu here that can be empty while it is still finding out. */
  @Test
  fun theExportPickerSaysItIsAskingWhileTheSpaceComesUp() {
    showTopBar(exportState = ExportState(loadingFormats = true))

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(EXPORT_NOTEBOOK).performClick()

    compose.onNodeWithText(LOADING_EXPORT_FORMATS).assertIsDisplayed()
  }

  /** A Space that offers nothing says so, rather than opening an empty menu. */
  @Test
  fun theExportPickerSaysSoWhenTheSpaceOffersNothing() {
    showTopBar(exportState = ExportState(offered = emptyList()))

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(EXPORT_NOTEBOOK).performClick()

    compose.onNodeWithText(NO_EXPORT_FORMATS).assertIsDisplayed()
  }

  private val headingsSetTo = mutableListOf<Boolean>()
  private val tableOfContentsSelections = mutableListOf<String>()
  private var tableOfContentsPanelActivations = 0
  private var debuggerPanelActivations = 0
  private var exportPickerOpens = 0
  private val exportedAs = mutableListOf<String>()

  private fun showTopBar(
    tableOfContentsHeadings: List<TocHeading> = emptyList(),
    onShowTableOfContentsPanel: (() -> Unit)? = null,
    onShowDebuggerPanel: (() -> Unit)? = null,
    exportState: ExportState = ExportState(offered = OFFERED_EXPORT_FORMATS),
  ) {
    compose.setContent {
      JupydroidTheme {
        EditorTopBar(
          canUndo = false,
          canRedo = false,
          onUndo = {},
          onRedo = {},
          onSetAllHeadingsCollapsed = { headingsSetTo += it },
          tableOfContentsHeadings = tableOfContentsHeadings,
          onTableOfContentsHeadingSelected = { tableOfContentsSelections += it },
          onShowTableOfContentsPanel = onShowTableOfContentsPanel,
          onShowDebuggerPanel = onShowDebuggerPanel,
          exportState = exportState,
          onExportPickerOpened = { exportPickerOpens++ },
          onExportAs = { exportedAs += it },
        )
      }
    }
  }
}
