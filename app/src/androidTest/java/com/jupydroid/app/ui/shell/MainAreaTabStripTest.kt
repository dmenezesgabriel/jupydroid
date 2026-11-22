package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Console
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.FileEditor
import com.jupydroid.app.Launcher
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.Settings
import com.jupydroid.app.SpacesHome
import com.jupydroid.app.Terminal
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.orderedAsOpened
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.extension.api.RENAME_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tab strip over the main area - `lm-DockPanel-tabBar`, the visible half of the
 * stack-as-tab-list.
 *
 * What the assertions pin is the reference's own anatomy: `lm-TabBar-tabIcon`, the `title.label`,
 * the `jp-mod-dirty` dot that occupies the close slot while a document owes a write
 * (`ui-components/style/icons.css:268-278`), and `saveBeforeClose` answering a dirty close
 * (`docmanager/src/dialogs.ts:155`). The browser is the list pane, so it gets no tab.
 *
 * The strip is exercised on its own here - a `NavKey` list and an [OpenDocuments] are all it asks
 * for - because the wiring half, that every detail entry draws one, is `MultiDocumentNavigationTest`'s.
 */
@RunWith(AndroidJUnit4::class)
class MainAreaTabStripTest {
  @get:Rule val compose = createComposeRule()

  /**
   * The workspace the strip's rename goes through - `context.rename` is a contents call, so the
   * fake records the ask rather than moving anything; adoption is the document's own business.
   */
  private class RecordingFiles : WorkspaceFiles by FakeWorkspaceFiles() {
    val renames = mutableListOf<Pair<String, String>>()
    val overwrites = mutableListOf<Pair<String, String>>()

    /** Set before the tap: the rename then refuses the way a 409 does. */
    var nameIsTaken = false

    /** Set before the tap: the disk refuses for any other reason, as `DiskWorkspaceFiles` does. */
    var renameRefusal: Throwable? = null
    var overwriteRefusal: Throwable? = null

    override suspend fun rename(path: String, name: String): String {
      if (nameIsTaken) throw FileNameTakenException(name)
      renameRefusal?.let { throw it }
      renames += path to name
      return movedTo(path, name)
    }

    override suspend fun overwrite(path: String, name: String): String {
      overwriteRefusal?.let { throw it }
      overwrites += path to name
      return movedTo(path, name)
    }

    /** `idBeside` - the name joined onto the folder, never treated as a path of its own. */
    private fun movedTo(path: String, name: String): String =
      path.substringBeforeLast('/', missingDelimiterValue = "").let { if (it.isEmpty()) name else "$it/$name" }
  }

  private var backStack = mutableStateListOf<NavKey>()
  private val open = OpenDocuments()
  private val workspace = RecordingFiles()
  private val paths = mutableStateMapOf<NavKey, String>()
  private val liveTitles = mutableStateMapOf<NavKey, String>()

  private fun strip(vararg entries: NavKey, twoPane: Boolean = true, root: NavKey = Explorer(BASE_SPACE.id)) {
    backStack = mutableStateListOf(root, *entries)
    compose.setContent {
      JupydroidTheme {
        // The same reconcile `MainNavigation` runs: the strip is handed opening order while the
        // stack keeps its own, which is what the activation tests below are exercising.
        val order = remember { mutableStateListOf<NavKey>() }
        val tabs = backStack.mainAreaTabs().orderedAsOpened(order)
        LaunchedEffect(tabs) {
          order.clear()
          order.addAll(tabs)
        }
        // The gate `MainNavigation` mounts for it - the strip's closes run through it.
        val closeTab = rememberMainAreaCloseGate(backStack, tabs, open, paths).closeTab
        TabbedDocumentPane(
          backStack, open, paths, tabs, liveTitles, FakeWorkspaceRepository(), workspace, twoPane = twoPane, closeTab,
          onOpenDocument = {}, documentCheckpoints = FakeDocumentCheckpoints(),
        ) {
          Text("the document")
        }
      }
    }
  }

  private fun tab(title: String) = compose.onNodeWithContentDescription("Tab: $title")

  private fun closeTab(title: String) = compose.onNodeWithContentDescription("Close tab: $title")

  @Test
  fun everyMainAreaEntryGetsATabAndTheBrowserDoesNot() {
    strip(Editor("a.ipynb"), FileEditor("b.py"), Terminal("1"))

    tab("a.ipynb").assertIsDisplayed()
    tab("b.py").assertIsDisplayed()
    tab("Terminal 1").assertIsDisplayed()
    compose.onAllNodes(hasContentDescription("Tab: Files"), useUnmergedTree = true)
      .fetchSemanticsNodes().let { assertTrue("the list pane is not a tab", it.isEmpty()) }
  }

  /** A tab's press is `activateById` - the entry moves to the top and its document shows. */
  @Test
  fun tappingATabRevealsItsDocument() {
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    compose.tabInReach("a.ipynb").performClick()

    assertEquals(Editor("a.ipynb"), backStack.last())
  }

  /** `application:close` - a clean document's tab leaves and the others stay. */
  @Test
  fun closingATabLeavesTheOthersOpen() {
    strip(Editor("a.ipynb"), FileEditor("b.py"), Terminal("1"))

    closeTab("b.py").performClick()

    assertEquals(listOf(Explorer(BASE_SPACE.id), Editor("a.ipynb"), Terminal("1")), backStack)
    tab("b.py").assertIsNotDisplayed()
    tab("Terminal 1").assertIsDisplayed()
  }

  /**
   * `jp-mod-dirty`: a document owing a write carries the dot in its close slot - the reference
   * swaps the close icon for the busy marker and keeps it the close button
   * (`icons.css:268-278`), which is also what a tab needs on a screen with no hover.
   */
  @Test
  fun aDirtyDocumentsTabWearsTheDot() {
    strip(Editor("a.ipynb"), FileEditor("b.py"))
    open.opened(FakeTabDocument("a.ipynb", isDirty = true))

    tab("a.ipynb").assertIsDisplayed()
    compose.waitUntil(5_000) {
      compose.onAllNodes(hasContentDescription("Unsaved changes"), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()
    }
  }

  /**
   * `saveBeforeClose` word for word (`dialogs.ts:155-180`): a dirty tab's close asks, Cancel keeps
   * the tab, Discard drops the work and the tab.
   */
  @Test
  fun closingADirtyTabAsksAndDiscardClosesIt() {
    val document = FakeTabDocument("a.ipynb", isDirty = true)
    open.opened(document)
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    closeTab("a.ipynb").performClick()
    compose.onNodeWithText("Save your work").assertIsDisplayed()
    compose.onNodeWithText("Cancel").performClick()

    assertTrue("Cancel leaves the tab", backStack.contains(Editor("a.ipynb")))
    assertEquals(0, document.discards)

    closeTab("a.ipynb").performClick()
    compose.onNodeWithText("Discard").performClick()

    assertEquals(1, document.discards)
    assertEquals(listOf(Explorer(BASE_SPACE.id), FileEditor("b.py")), backStack)
  }

  /** Save writes first and closes only when nothing is left owed - the flush's own continuation. */
  @Test
  fun closingADirtyTabOnSaveWritesThenCloses() {
    val document = FakeTabDocument("a.ipynb", isDirty = true)
    open.opened(document)
    strip(Editor("a.ipynb"))

    closeTab("a.ipynb").performClick()
    compose.onNodeWithText("Save").performClick()

    assertEquals(1, document.savesRequested)
    assertFalse(backStack.contains(Editor("a.ipynb")))
  }

  /**
   * A document that is **not** on top asks the same way - JupyterLab's close does not activate the
   * widget first (`widgetmanager.ts` closes where it stands), so the dialog appears over whatever
   * is current.
   */
  @Test
  fun aDirtyTabThatIsNotOnTopStillAsksBeforeClosing() {
    val document = FakeTabDocument("a.ipynb", isDirty = true)
    open.opened(document)
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    closeTab("a.ipynb").performClick()

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    assertEquals("the current tab never moved", FileEditor("b.py"), backStack.last())
  }

  /**
   * `main.title.closable = widgets('main').length > 1` (`launcher-extension/src/index.ts:126`): a
   * launcher alone in the main area draws no close affordance, and beside another tab it does.
   */
  @Test
  fun aLoneLauncherTabDrawsNoCloseAndASecondTabGivesItOne() {
    strip(Launcher)
    compose.onAllNodes(hasContentDescription("Close tab: Launcher"), useUnmergedTree = true)
      .fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }

    // The count is read live: a second main-area widget arriving gives the launcher's its close.
    backStack.add(backStack.size - 1, Editor("a.ipynb"))
    compose.waitForIdle()

    closeTab("Launcher").assertIsDisplayed()
  }

  /** Settings and Console are tabs like the documents - the plan's own list. */
  @Test
  fun settingsAndConsoleAreTabsToo() {
    strip(Editor("a.ipynb"), Settings, Console("p", "Console 1"))

    tab("Settings").assertIsDisplayed()
    tab("Console 1").assertIsDisplayed()
  }

  /**
   * `activateById` changes the current widget and **never the tab's place** - this is the
   * regression the whole fix is for: the stack promotes the pressed entry (the scene draws its
   * top), but the strip reads `orderedAsOpened` and the row does not move.
   */
  @Test
  fun clickingATabActivatesItWithoutMovingIt() {
    strip(Editor("a.ipynb"), FileEditor("b.py"), Terminal("1"))

    fun tabsLeftToRight(): List<String> = compose.onAllNodes(hasContentDescription("Tab: ", substring = true))
      .fetchSemanticsNodes()
      .sortedBy { it.boundsInRoot.left }
      .map { it.config[SemanticsProperties.ContentDescription].joinToString() }

    assertEquals(listOf("Tab: a.ipynb", "Tab: b.py", "Tab: Terminal 1"), tabsLeftToRight())

    compose.tabInReach("a.ipynb").performClick()

    assertEquals(Editor("a.ipynb"), backStack.last())
    assertEquals(listOf("Tab: a.ipynb", "Tab: b.py", "Tab: Terminal 1"), tabsLeftToRight())
  }

  /**
   * A long press is a touch screen's right-click, so it opens the tab's context menu: Rename, the
   * `document-title`-bound `docmanager:rename` (`docmanager-extension/schema/plugin.json:75`), and
   * Save As, which VS Code's tab menu carries beside it.
   */
  @Test
  fun aLongPressOnADocumentTabOffersRenameAndSaveAs() {
    open.opened(FakeTabDocument("dir/a.ipynb"))
    strip(Editor("dir/a.ipynb"), FileEditor("b.py"))

    compose.tabInReach("a.ipynb").performTouchInput { longClick() }

    compose.onNodeWithText("Rename…").assertIsDisplayed()
    compose.onNodeWithText("Save As…").assertIsDisplayed()
  }

  /** Rename on the menu opens the same RenameFileDialog the row and the browser share. */
  @Test
  fun renameOnTheTabMenuOpensTheRenameDialog() {
    strip(Editor("dir/a.ipynb"), FileEditor("b.py"))

    compose.tabInReach("a.ipynb").performTouchInput { longClick() }
    compose.onNodeWithText("Rename…").performClick()

    compose.onNodeWithText("Rename file").assertIsDisplayed()
    compose.onNodeWithTag(RENAME_FIELD_TAG).assertIsDisplayed()
  }

  /** A tab without a path has nothing to rename or save - its menu holds no document row. */
  @Test
  fun aLongPressOnATerminalTabOffersNoDocumentRow() {
    strip(Terminal("1"), Editor("a.ipynb"))

    tab("Terminal 1").performTouchInput { longClick() }

    compose.onAllNodesWithText("Rename…").assertCountEquals(0)
  }

  /** The ask goes to the workspace - `context.rename` is a contents call in the reference too. */
  @Test
  fun confirmingTheRenameAsksTheWorkspaceToMoveTheDocument() {
    open.opened(FakeTabDocument("dir/a.ipynb"))
    strip(Editor("dir/a.ipynb"), FileEditor("b.py"))

    renameTab("a.ipynb", to = "renamed.ipynb")

    compose.waitUntil(5_000) { workspace.renames.isNotEmpty() }
    assertEquals(listOf("dir/a.ipynb" to "renamed.ipynb"), workspace.renames)
  }

  /**
   * `renameFile`'s 409 becomes `shouldOverwrite` and then `overwrite`
   * (`packages/docmanager/src/dialogs.ts`) - the same two-step the explorer's row rename runs.
   */
  @Test
  fun aTakenNameAsksToOverwrite() {
    workspace.nameIsTaken = true
    strip(Editor("a.ipynb"))

    renameTab("a.ipynb", to = "taken.ipynb")

    compose.onNodeWithText("Overwrite file?").assertIsDisplayed()
    compose.onNodeWithText("Overwrite").performClick()

    compose.waitUntil(5_000) { workspace.overwrites.isNotEmpty() }
    assertEquals(listOf("a.ipynb" to "taken.ipynb"), workspace.overwrites)
  }

  /**
   * A refusal that is not a taken name says why - the browser's `_doRename` answers every one but
   * the user's own cancel with `showErrorMessage('Rename Error', error)`
   * (`packages/filebrowser/src/listing.ts`), where a closed dialog alone would leave the user
   * guessing.
   */
  @Test
  fun aRenameTheDiskRefusesSaysWhy() {
    workspace.renameRefusal = IllegalArgumentException("nothing at 'a.ipynb' in the workspace to rename")
    strip(Editor("a.ipynb"))

    renameTab("a.ipynb", to = "b.ipynb")

    compose.waitUntil(5_000) { compose.onAllNodesWithText("Rename Error").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("nothing at 'a.ipynb' in the workspace to rename").assertIsDisplayed()
    compose.onNodeWithText("OK").performClick()
    compose.onAllNodesWithText("Rename Error").assertCountEquals(0)
  }

  /** The overwrite after a taken name is refused the same way, and says so the same way. */
  @Test
  fun anOverwriteTheDiskRefusesSaysWhy() {
    workspace.nameIsTaken = true
    workspace.overwriteRefusal = IllegalStateException("could not replace 'full' in the workspace")
    strip(Editor("a.ipynb"))

    renameTab("a.ipynb", to = "full")
    compose.onNodeWithText("Overwrite").performClick()

    compose.waitUntil(5_000) { compose.onAllNodesWithText("Rename Error").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("could not replace 'full' in the workspace").assertIsDisplayed()
  }

  private fun renameTab(title: String, to: String) {
    compose.tabInReach(title).performTouchInput { longClick() }
    compose.onNodeWithText("Rename…").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput(to)
    compose.onNodeWithText("Rename").performClick()
  }

  /**
   * The shell's OSC title lands on the tab, not on a panel row - `term.onTitleChange` sets
   * `title.label` (`packages/terminal/src/widget.ts:486-488`), and the label is the strip's.
   * A terminal that has not reported one keeps "Terminal N".
   */
  @Test
  fun aTerminalTabWearsTheShellsReportedTitle() {
    strip(Terminal("1"))

    tab("Terminal 1").assertIsDisplayed()

    liveTitles[Terminal("1")] = "user@tablet:~/reports"
    compose.waitForIdle()

    tab("user@tablet:~/reports").assertIsDisplayed()
  }

  /**
   * A rename that lands while the document is **not** the top entry still retitles its tab:
   * the label reads the document's live `path` - found through `openedAs`, the one address a
   * rename cannot move - not the path the key was opened with.
   */
  @Test
  fun aTabTitleFollowsTheDocumentsLivePathThroughARename() {
    val document = FakeTabDocument("a.ipynb")
    open.opened(document)
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    document.livePath = "renamed.ipynb"
    compose.waitForIdle()

    tab("renamed.ipynb").assertIsDisplayed()
  }

  /**
   * At one pane the strip leads with the Up affordance - the navigation icon Material gives a
   * detail pane's top bar, and the tab row is where IntelliJ's own Back/Forward live
   * (`EditorTabbedContainer`). The label names the **destination**: over the browser it is
   * "Back to files", not the system bar's bare "Back" an earlier flow sweep collided with.
   */
  @Test
  fun atOnePaneTheStripLeadsWithAnArrowThatNamesTheBrowser() {
    strip(Editor("a.ipynb"), twoPane = false)

    compose.onNodeWithContentDescription("Back to files").assertIsDisplayed()
  }

  /**
   * The Spaces home is beneath a document only for the frames a popped entry still draws while it
   * leaves - and it is named like any other destination rather than refused as a non-tab.
   */
  @Test
  fun atOnePaneTheArrowOverTheHomeNamesTheSpaces() {
    strip(Editor("a.ipynb"), twoPane = false, root = SpacesHome)

    compose.onNodeWithContentDescription("Back to Spaces").assertIsDisplayed()
  }

  /** Beside a browser there is nothing to go back to - closing is the tab's ✕, so no arrow. */
  @Test
  fun atTwoPanesTheStripDrawsNoArrow() {
    strip(Editor("a.ipynb"), twoPane = true)

    compose.onAllNodes(hasContentDescription("Back", substring = true)).assertCountEquals(0)
  }

  /**
   * The arrow leaves the way the old pane dismiss did: the top entry is removed and whatever was
   * beneath it shows - `closeMainAreaTab`, the very path the ✕ takes, because an arrow and a cross
   * emptying the same area are the one act the reference calls `application:close`.
   */
  @Test
  fun theArrowClosesTheTopDocumentAndRevealsWhatWasUnderIt() {
    strip(Editor("a.ipynb"), twoPane = false)

    compose.onNodeWithContentDescription("Back to files").performClick()

    assertEquals(listOf(Explorer(BASE_SPACE.id)), backStack)
  }

  /**
   * The destination names what is *actually* beneath: a Console over its notebook goes "Back to"
   * that document, not to a canned label - the old per-screen strings were written by callers that
   * could only guess what was under them.
   */
  @Test
  fun overAnotherDocumentTheArrowNamesThatDocument() {
    strip(Editor("a.ipynb"), Console("a.ipynb"), twoPane = false)

    compose.onNodeWithContentDescription("Back to a.ipynb").assertIsDisplayed()
  }

  /** A dirty document asks before the arrow takes it away - the same `saveBeforeClose` the ✕ runs. */
  @Test
  fun theArrowOnADirtyDocumentAsksFirst() {
    open.opened(FakeTabDocument("a.ipynb", isDirty = true))
    strip(Editor("a.ipynb"), twoPane = false)

    compose.onNodeWithContentDescription("Back to files").performClick()

    compose.onNodeWithText("Save your work").assertIsDisplayed()
    assertTrue("the ask, not the pop", backStack.contains(Editor("a.ipynb")))
  }

  /**
   * `main.title.closable` rules the ✕, not the arrow: a lone launcher at one pane still gets the
   * way back, because there *is* something beneath it here - the browser.
   */
  @Test
  fun aLoneLauncherAtOnePaneStillDrawsTheArrow() {
    strip(Launcher, twoPane = false)

    compose.onNodeWithContentDescription("Back to files").assertIsDisplayed()

    compose.onNodeWithContentDescription("Back to files").performClick()

    assertEquals(listOf(Explorer(BASE_SPACE.id)), backStack)
  }
}
