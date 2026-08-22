package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.theme.JupyterDroidTheme
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

  /**
   * The row **names what tapping will do** rather than carrying a check beside a fixed label.
   *
   * That is this app's own rule - `COLLAPSE_CODE`/`EXPAND_CODE` in [cellActionItems] - and here it
   * is load-bearing rather than tidy: a toggled `DropdownMenuItem`'s state lives on a node
   * Android's accessibility tree gives no label to, so a Maestro flow cannot read it
   * (`docs/DECISIONS.md`). A label that flips is a label both trees can see.
   */
  @Test
  fun theNotebookMenuOffersToHideTheGutterWhileItIsShown() {
    showTopBar(lineNumbersShown = true)

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(HIDE_LINE_NUMBERS).assertIsDisplayed()
  }

  @Test
  fun theNotebookMenuOffersToShowTheGutterOnceItIsHidden() {
    showTopBar(lineNumbersShown = false)

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(SHOW_LINE_NUMBERS).assertIsDisplayed()
  }

  @Test
  fun theRowAsksForTheToggle() {
    showTopBar(lineNumbersShown = true)

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(HIDE_LINE_NUMBERS).performClick()

    assertEquals(1, lineNumberToggles)
  }

  @Test
  fun theNotebookMenuOffersToDisableBracketsWhileTheyAreOn() {
    showTopBar(settings = EditorSettings(autoClosingBrackets = true))

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(DISABLE_AUTO_CLOSING_BRACKETS).assertIsDisplayed()
  }

  @Test
  fun theNotebookMenuOffersToEnableBracketsOnceTheyAreOff() {
    showTopBar(settings = EditorSettings(autoClosingBrackets = false))

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(ENABLE_AUTO_CLOSING_BRACKETS).assertIsDisplayed()
  }

  @Test
  fun theBracketsRowAsksForTheToggle() {
    showTopBar(settings = EditorSettings(autoClosingBrackets = true))

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(DISABLE_AUTO_CLOSING_BRACKETS).performClick()

    assertEquals(listOf(EditorSettings(autoClosingBrackets = false)), settingsWritten)
  }

  /**
   * Off until asked for, so the row offers to turn it **on** - the reference's own default and its
   * own instruction to the user (`docs/source/user/notebook.md:191`).
   */
  @Test
  fun theNotebookMenuOffersToTurnKernelHistoryOnWhileItIsOff() {
    showTopBar(settings = EditorSettings(accessKernelHistory = false))

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(ENABLE_KERNEL_HISTORY).assertIsDisplayed()
  }

  @Test
  fun theNotebookMenuOffersToTurnKernelHistoryOffOnceItIsOn() {
    showTopBar(settings = EditorSettings(accessKernelHistory = true))

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(DISABLE_KERNEL_HISTORY).assertIsDisplayed()
  }

  @Test
  fun theKernelHistoryRowFlipsThatFieldAndNoOther() {
    showTopBar(settings = EditorSettings(accessKernelHistory = false))

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(ENABLE_KERNEL_HISTORY).performClick()

    compose.runOnIdle { assertEquals(listOf(EditorSettings(accessKernelHistory = true)), settingsWritten) }
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
   * With a pane to show, the row asks for it rather than opening an overlay - the editor is
   * drawing the list itself, so a second copy over the top of it would be two answers to one
   * question.
   */
  @Test
  fun inPaneModeTheRowAsksForThePaneAndOpensNoOverlay() {
    showTopBar(
      tableOfContentsHeadings = listOf(TocHeading("h1", 1, "Chapter one")),
      tableOfContentsPresentation = TocPresentation.PANE,
    )

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(SHOW_TABLE_OF_CONTENTS).performClick()

    compose.runOnIdle { assertEquals(1, tableOfContentsPaneToggles) }
    compose.onNodeWithContentDescription("Table of contents Chapter one").assertDoesNotExist()
  }

  /**
   * **The label flips with the state**, which is this app's own convention for a toggle
   * ([SHOW_LINE_NUMBERS]) and load-bearing for the same reason: Android's accessibility tree
   * keeps a `DropdownMenuItem`'s toggled state on a node carrying no label, so a check mark is a
   * state no Maestro flow can read.
   */
  @Test
  fun theRowNamesTheOppositeOfWhatIsOnScreen() {
    showTopBar(tableOfContentsPresentation = TocPresentation.PANE, tableOfContentsPaneOpen = true)

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(HIDE_TABLE_OF_CONTENTS).assertIsDisplayed()
  }

  @Test
  fun theNotebookMenuOffersToNumberTheHeadings() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(SHOW_HEADING_NUMBERS).performClick()

    compose.runOnIdle { assertEquals(listOf(EditorSettings(tocNumberHeaders = true)), settingsWritten) }
  }

  @Test
  fun withNumbersOnTheRowOffersToTakeThemAway() {
    showTopBar(settings = EditorSettings(tocNumberHeaders = true))

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(HIDE_HEADING_NUMBERS).assertIsDisplayed()
  }

  /**
   * The first-level row is the reference's own `isEnabled` read through this app's rule for a
   * control that would do nothing: with nothing numbered, both of its states look identical.
   */
  @Test
  fun theTopLevelRowIsOfferedOnlyWhileSomethingIsNumbered() {
    showTopBar()

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(HIDE_TOP_LEVEL_NUMBER).assertDoesNotExist()
    compose.onNodeWithText(SHOW_TOP_LEVEL_NUMBER).assertDoesNotExist()
  }

  @Test
  fun theTopLevelRowFlipsWithItsOwnState() {
    showTopBar(settings = EditorSettings(tocNumberHeaders = true, tocNumberingH1 = true))

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(HIDE_TOP_LEVEL_NUMBER).performClick()

    compose.runOnIdle {
      assertEquals(listOf(EditorSettings(tocNumberHeaders = true, tocNumberingH1 = false)), settingsWritten)
    }
  }

  @Test
  fun theNotebookMenuOffersToLeaveOutWhatACellPrinted() {
    showTopBar(settings = EditorSettings(tocIncludeOutput = true))

    compose.onNodeWithContentDescription("Notebook actions").performClick()
    compose.onNodeWithText(HIDE_OUTPUT_HEADINGS).performClick()

    compose.runOnIdle { assertEquals(listOf(EditorSettings(tocIncludeOutput = false)), settingsWritten) }
  }

  @Test
  fun withThemLeftOutTheRowOffersToPutThemBack() {
    showTopBar(settings = EditorSettings(tocIncludeOutput = false))

    compose.onNodeWithContentDescription("Notebook actions").performClick()

    compose.onNodeWithText(SHOW_OUTPUT_HEADINGS).assertIsDisplayed()
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
   * (`evidence/p2j-2-export-formats/`) - see [OFFERED_EXPORT_FORMATS].
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
  private var lineNumberToggles = 0
  private val tableOfContentsSelections = mutableListOf<String>()
  private var tableOfContentsPaneToggles = 0
  private var exportPickerOpens = 0
  private val exportedAs = mutableListOf<String>()

  /** What the View menu asked to persist - one list for every toggle, as the menu writes them. */
  private val settingsWritten = mutableListOf<EditorSettings>()

  private fun showTopBar(
    lineNumbersShown: Boolean = true,
    settings: EditorSettings = EditorSettings(),
    tableOfContentsHeadings: List<TocHeading> = emptyList(),
    tableOfContentsPresentation: TocPresentation = TocPresentation.OVERLAY,
    tableOfContentsPaneOpen: Boolean = false,
    exportState: ExportState = ExportState(offered = OFFERED_EXPORT_FORMATS),
  ) {
    compose.setContent {
      JupyterDroidTheme {
        EditorTopBar(
          title = "Intro to NumPy",
          canUndo = false,
          canRedo = false,
          onRename = {},
          onUndo = {},
          onRedo = {},
          onBack = {},
          onSetAllHeadingsCollapsed = { headingsSetTo += it },
          lineNumbersShown = lineNumbersShown,
          onToggleLineNumbers = { lineNumberToggles++ },
          settings = settings,
          onSettingsChanged = { settingsWritten += it },
          tableOfContentsHeadings = tableOfContentsHeadings,
          onTableOfContentsHeadingSelected = { tableOfContentsSelections += it },
          tableOfContentsPresentation = tableOfContentsPresentation,
          tableOfContentsPaneOpen = tableOfContentsPaneOpen,
          onToggleTableOfContentsPane = { tableOfContentsPaneToggles++ },
          exportState = exportState,
          onExportPickerOpened = { exportPickerOpens++ },
          onExportAs = { exportedAs += it },
        )
      }
    }
  }
}
