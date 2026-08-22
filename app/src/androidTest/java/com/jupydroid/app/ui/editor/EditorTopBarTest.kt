package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RENAME_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notebook's title is where its rename lives, which is where JupyterLab puts it -
 * `docmanager:rename` hangs off `[data-type="document-title"]`
 * (`packages/docmanager-extension/schema/plugin.json:73-77`).
 *
 * Instrumented because "is there something here to press" is not a question a JVM test can
 * answer, which is the whole reason `CellHeaderTest` exists.
 */
@RunWith(AndroidJUnit4::class)
class EditorTopBarTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theTitleIsOnScreen() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").assertIsDisplayed()
  }

  @Test
  fun tappingTheTitleOpensTheRenameDialog() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()

    compose.onNodeWithText("Rename notebook").assertIsDisplayed()
  }

  @Test
  fun renamingFromTheTitleReportsTheNewTitle() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Linear algebra")
    compose.onNodeWithText("Rename").performClick()

    assertEquals(listOf("Linear algebra"), renamed)
  }

  @Test
  fun cancellingRenamesNothing() {
    showTopBar()

    compose.onNodeWithText("Intro to NumPy").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput("Linear algebra")
    compose.onNodeWithText("Cancel").performClick()

    assertEquals(emptyList<String>(), renamed)
  }

  /**
   * The bar carries the title and nothing that belongs to the kernel.
   *
   * This used to assert the opposite - that Run All was still here - which was the right guard
   * while Run All lived on this bar. It moved to [NotebookBar], beside interrupt and restart and
   * within reach of a thumb, so the assertion is inverted rather than deleted: a kernel action
   * drifting back onto the app bar is exactly the regression worth catching, and `NotebookBarTest`
   * covers Run All being reachable in its new home.
   */
  @Test
  fun theBarCarriesNoKernelActions() {
    showTopBar()

    compose.onNodeWithContentDescription("Run all cells").assertDoesNotExist()
  }

  // --- P2a: undo and redo of cell operations ---

  /**
   * Both are on the bar even with nothing to take back, disabled rather than absent.
   *
   * A control that appears only once it would work is one nobody discovers, which is the lesson
   * the cell menu cost three attempts (`docs/DECISIONS.md`). JupyterLab keeps both items in the
   * Edit menu at all times and gates them with `isEnabled` (`notebook-extension/src/index.ts:4485`).
   */
  @Test
  fun bothAreOnTheBarAndDisabledWithNothingToTakeBack() {
    showTopBar(canUndo = false, canRedo = false)

    compose.onNodeWithContentDescription("Undo cell operation").assertIsDisplayed().assertIsNotEnabled()
    compose.onNodeWithContentDescription("Redo cell operation").assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun undoBecomesPressableOnceThereIsSomethingToTakeBack() {
    showTopBar(canUndo = true, canRedo = false)

    compose.onNodeWithContentDescription("Undo cell operation").performClick()

    assertEquals(1, undos)
    compose.onNodeWithContentDescription("Redo cell operation").assertIsNotEnabled()
  }

  @Test
  fun redoBecomesPressableOnceSomethingHasBeenTakenBack() {
    showTopBar(canUndo = false, canRedo = true)

    compose.onNodeWithContentDescription("Redo cell operation").performClick()

    assertEquals(1, redos)
  }

  // --- the View menu (P2f-2) --------------------------------------------------------------

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

  // --- the line-number gutter (P2g-1) -----------------------------------------------------

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

  // --- notebook:toggle-autoclosing-brackets, universal (P2g-3) ----------------------------

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

  // --- notebook:access-*-history-entry, the setting that gates them (P2k-3) ---------------

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

  // --- the table of contents (P2h-2) -------------------------------------------------------

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

  // --- the persistent pane (P2h-3) ---------------------------------------------------------

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

  // --- toc:display-numbering / display-h1-numbering (P2h-5) --------------------------------

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

  // --- toc:display-outputs-numbering (P2h-6) ------------------------------------------------

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

  private val renamed = mutableListOf<String>()
  private var finds = 0
  private var undos = 0
  private var redos = 0
  private val headingsSetTo = mutableListOf<Boolean>()
  private var lineNumberToggles = 0
  private val tableOfContentsSelections = mutableListOf<String>()
  private var tableOfContentsPaneToggles = 0

  /** What the View menu asked to persist - one list for every toggle, as the menu now writes them. */
  private val settingsWritten = mutableListOf<EditorSettings>()

  /**
   * `documentsearch:start`, and it is on the bar rather than in the View menu - so it is asserted
   * where a thumb finds it, one tap in.
   */
  @Test
  fun findIsOneTapFromTheNotebooksOwnBar() {
    showTopBar()

    compose.onNodeWithContentDescription(FIND_ACTION).performClick()

    assertEquals(1, finds)
  }

  // --- P2j: notebook:export-to-format ---

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

  // --- the title's own row (P2q) ---------------------------------------------------------

  /**
   * **The title is on a row of its own, above the actions.** That is the reference's arrangement
   * in single-document mode - the mode that shows one document at a time, which is this app's
   * shape: `#jp-title-panel` is a strip holding nothing but the document's name
   * (`packages/application/style/titlepanel.css:10-37`), inserted above the document's own toolbar
   * by `LabShell.mode`'s setter (`packages/application/src/shell.ts:690-705`).
   *
   * Asserted as a geometry rather than as a hierarchy: what the reader sees is that the name does
   * not share a line with the buttons, and a node's bounds are what say so.
   */
  @Test
  fun theTitleSitsAboveTheActions() {
    showTopBar()

    val title = compose.onNodeWithText("Intro to NumPy").getUnclippedBoundsInRoot()
    val undo = compose.onNodeWithContentDescription("Undo cell operation").getUnclippedBoundsInRoot()
    assertTrue("title $title overlaps the actions at $undo", title.bottom <= undo.top)
  }

  /**
   * **The whole file name is on screen at 320dp**, which is the width this app is driven at and
   * the one that decides. It was not: sharing the bar with undo, redo, search and the `⋮` left the
   * name about 64dp and it truncated to `Gutt…` - `evidence/p2n-gutter/before/` and `after/`, both
   * of them, since the bar was not what those flows were about.
   *
   * Measured against the same string drawn with nothing competing for the row, because Compose's
   * semantics carry the full text whether or not it fits: `onNodeWithText` finds a truncated title
   * exactly as it finds a whole one, so only the width can tell them apart.
   */
  @Test
  fun theWholeFileNameFitsAtTheNarrowestWidth() {
    compose.setContent {
      JupyterDroidTheme {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(COMPACT)) {
          Column {
            EditorTopBar(
              title = LONG_NAME,
              canUndo = true,
              canRedo = true,
              onRename = {},
              onUndo = {},
              onRedo = {},
              onBack = {},
            )
            Text(
              text = LONG_NAME,
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.testTag(UNCONSTRAINED_TITLE),
            )
          }
        }
      }
    }

    val drawn = compose.onAllNodesWithText(LONG_NAME)[0].getUnclippedBoundsInRoot()
    val unconstrained = compose.onNodeWithTag(UNCONSTRAINED_TITLE).getUnclippedBoundsInRoot()
    val shown = drawn.right - drawn.left
    val wanted = unconstrained.right - unconstrained.left
    assertTrue("the title draws $shown of the $wanted it wants", shown >= wanted)
  }

  private var exportPickerOpens = 0
  private val exportedAs = mutableListOf<String>()

  private fun showTopBar(
    canUndo: Boolean = false,
    canRedo: Boolean = false,
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
          canUndo = canUndo,
          canRedo = canRedo,
          onRename = { renamed += it },
          onUndo = { undos++ },
          onRedo = { redos++ },
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
          onFind = { finds++ },
          onToggleTableOfContentsPane = { tableOfContentsPaneToggles++ },
          exportState = exportState,
          onExportPickerOpened = { exportPickerOpens++ },
          onExportAs = { exportedAs += it },
        )
      }
    }
  }

  private companion object {
    /** The emulator's own window, and the narrowest this app is driven at. */
    val COMPACT = DpSize(320.dp, 640.dp)

    /** A real notebook name, long enough that a shared bar has to cut it. */
    const val LONG_NAME = "Heading Sections.ipynb"

    /** The same string with a whole row to itself, which is the width the title wants. */
    const val UNCONSTRAINED_TITLE = "unconstrained-title"
  }
}
