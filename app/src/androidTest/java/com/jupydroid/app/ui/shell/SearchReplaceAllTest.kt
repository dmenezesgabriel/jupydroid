package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.SHOW_REPLACE
import com.jupydroid.app.ui.scrollUntilFound
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Replace All on the search panel - the button, the counted question, and what it leaves behind.
 *
 * `SearchView.replaceAll` drawn (`searchView.ts:812-853`): the action is only enabled once a walk
 * has found something (`:792`), the confirmation counts what is on screen, and the report is the
 * same sentence in the past tense. VS Code answers both halves of this panel - JupyterLab registers
 * no workspace search at all.
 *
 * Its own class rather than more of `SearchSidePanelTest`, on the seam detekt and the file-length
 * gate both found in P2x-5g: *the workspace searched* and *the workspace rewritten*.
 */
@RunWith(AndroidJUnit4::class)
class SearchReplaceAllTest {
  @get:Rule val compose = createComposeRule()

  /** What a replace writes to, seeded apart from what the walk finds - see `WorkspaceReplaceAllTest`. */
  private var workspace = FakeWorkspaceRepository()

  private fun show(vararg files: Pair<String, String>) {
    workspace = FakeWorkspaceRepository(textByPath = files.toMap())
    val replace = WorkspaceReplace(OpenDocuments(), FakeNotebookRepository(), workspace)
    compose.setContent {
      JupyterDroidTheme {
        SearchSidePanel(
          searcher = FakeWorkspaceSearcher(files.toMap()),
          replace = replace,
          onOpenMatch = { _, _ -> },
        )
      }
    }
  }

  /** The panel is one scrolling surface, so a node below the fold has to be scrolled to. */
  private fun scrollTo(what: SemanticsMatcher) =
    scrollUntilFound(compose, hasScrollAction(), what) { "the panel never scrolled to $what" }

  /**
   * Opens the replace row, types a query and a replacement, and **scrolls to** what should appear.
   *
   * Scrolling rather than waiting, and it took a red test to say so: with the replace row open the
   * panel's fixed chrome is taller than this window, so a `LazyColumn` never composes the second
   * file's group at all - the one-file cases here went green on a bare `waitUntil` and every
   * two-file case timed out on a node that was correct and not composed. [scrollTo] is both halves,
   * because the walk has 300 ms of debounce to spend before there is anything to scroll to.
   */
  private fun searchAndReplace(query: String, replacement: String, until: SemanticsMatcher) {
    compose.onNodeWithContentDescription(SHOW_REPLACE).performClick()
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performTextInput(query)
    compose.onNodeWithContentDescription(REPLACE_IN_WORKSPACE_BOX).performTextInput(replacement)
    scrollTo(until)
  }

  private fun textOf(path: String) = runBlocking { workspace.readText(path) }

  /**
   * `REPLACE_ALL_DISABLED_LABEL` - *"Replace All (Submit Search to Enable)"* (`searchWidget.ts:118`).
   *
   * **Disabled and drawn, not hidden**: the reference's own label explains why the control cannot be
   * pressed, which a control that is simply absent cannot do. It is the one place this app draws a
   * button that would fail, and it is drawn precisely because it says so.
   */
  @Test
  fun replaceAllIsDrawnDisabledUntilSomethingIsFound() {
    show("notes.py" to "cat = 1")
    compose.onNodeWithContentDescription(SHOW_REPLACE).performClick()

    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).assertIsNotEnabled()
    compose.onNodeWithContentDescription(REPLACE_ALL_DISABLED).assertExists()
  }

  /** `setReplaceAllActionState(!searchResult.isEmpty())` (`searchView.ts:792`). */
  @Test
  fun replaceAllIsEnabledOnceTheWalkHasFoundSomething() {
    show("notes.py" to "cat = 1")

    searchAndReplace("cat", "dog", until = hasText("cat = 1"))

    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).assertIsEnabled()
  }

  /**
   * The question is **counted** - `buildReplaceAllConfirmationMessage` over the tree's own counts
   * (`searchView.ts:816-817`, `:832`), with the reference's title and primary button beside it.
   */
  @Test
  fun theQuestionCountsWhatIsOnScreenAndNamesTheReplacement() {
    show("a.py" to "cat = 1", "b.py" to "cat cat")

    searchAndReplace("cat", "dog", until = hasText("cat cat"))
    scrollTo(hasText(REPLACE_ALL_IN_WORKSPACE))
    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).performClick()

    compose.onNodeWithText(REPLACE_ALL_TITLE).assertIsDisplayed()
    compose.onNodeWithText("Replace 3 occurrences across 2 files with 'dog'?").assertIsDisplayed()
    compose.onNodeWithText(REPLACE_ALL_CONFIRM).assertIsDisplayed()
  }

  /** *"Cancel"* is the dismiss button, and a dismissed question writes nothing. */
  @Test
  fun cancellingTheQuestionWritesNothing() {
    show("a.py" to "cat = 1")

    searchAndReplace("cat", "dog", until = hasText("cat = 1"))
    scrollTo(hasText(REPLACE_ALL_IN_WORKSPACE))
    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).performClick()
    compose.onNodeWithText(REPLACE_ALL_CANCEL).performClick()

    compose.onNodeWithText(REPLACE_ALL_TITLE).assertDoesNotExist()
    assertEquals("cat = 1", textOf("a.py"))
  }

  /**
   * Confirmed: every file written, the tree emptied (`textSearchHeading.ts:299-303`) and the report
   * drawn in the past tense.
   */
  @Test
  fun confirmingWritesEveryFileAndReportsWhatItWrote() {
    show("a.py" to "cat = 1", "b.py" to "cat = 2")

    searchAndReplace("cat", "dog", until = hasText("cat = 2"))
    scrollTo(hasText(REPLACE_ALL_IN_WORKSPACE))
    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).performClick()
    compose.onNodeWithText(REPLACE_ALL_CONFIRM).performClick()
    scrollTo(hasText("Replaced 2 occurrences across 2 files with 'dog'."))

    assertEquals("dog = 1", textOf("a.py"))
    assertEquals("dog = 2", textOf("b.py"))
    compose.onNodeWithText("cat = 1").assertDoesNotExist()
  }

  /**
   * A resource that moved on between the walk and the tap refuses **the whole operation**, quoting
   * `_validateBeforePrepare`'s own sentence (`bulkTextEdits.ts:216`) - and the tree stands, because
   * it still describes what is on disk.
   */
  @Test
  fun aFileThatChangedRefusesTheWholeReplaceAndSaysWhich() {
    show("a.py" to "cat = 1", "b.py" to "cat = 2")

    searchAndReplace("cat", "dog", until = hasText("cat = 2"))
    scrollTo(hasText(REPLACE_ALL_IN_WORKSPACE))
    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).performClick()
    runBlocking { workspace.writeText("a.py", "someone else got here first") }
    compose.onNodeWithText(REPLACE_ALL_CONFIRM).performClick()
    scrollTo(hasText("a.py has changed in the meantime"))

    assertEquals("cat = 2", textOf("b.py"))
    scrollTo(hasText("cat = 2"))
  }

  // --- the undo, which is this panel's own and not the reference's -------------------------------

  /**
   * **One undo across every file, offered beside the sentence that reports the replace.**
   *
   * The reference undoes a replace-all from an *editor* - `MultiModelEditStackElement` on the
   * workspace undo stack, reached with Ctrl+Z - and its search view never learns that it happened.
   * There is no such path here and the notebook's own history deliberately does not carry a
   * workspace replace (`WorkspaceReplace`), so the panel that performed it is what offers to take it
   * back. The **results come back with the files**, which is what says so: nothing else on this
   * panel would have changed.
   */
  @Test
  fun theReportOffersAnUndoAndTakingItPutsEveryFileBack() {
    show("a.py" to "cat = 1", "b.py" to "cat = 2")

    searchAndReplace("cat", "dog", until = hasText("cat = 2"))
    scrollTo(hasText(REPLACE_ALL_IN_WORKSPACE))
    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).performClick()
    compose.onNodeWithText(REPLACE_ALL_CONFIRM).performClick()
    scrollTo(hasText(UNDO_THE_REPLACE))
    compose.onNodeWithText(UNDO_THE_REPLACE).performClick()
    scrollTo(hasText("cat = 2"))

    assertEquals("cat = 1", textOf("a.py"))
    assertEquals("cat = 2", textOf("b.py"))
    compose.onNodeWithText(UNDO_THE_REPLACE).assertDoesNotExist()
  }

  /**
   * A refusal wrote nothing, so there is nothing to take back - and the offer is **absent** rather
   * than disabled, which is the split `REPLACE_ALL_DISABLED` records: a disabled Replace All states
   * its own precondition, and an Undo beside a refusal would state nothing at all.
   */
  @Test
  fun aRefusedReplaceOffersNoUndo() {
    show("a.py" to "cat = 1", "b.py" to "cat = 2")

    searchAndReplace("cat", "dog", until = hasText("cat = 2"))
    scrollTo(hasText(REPLACE_ALL_IN_WORKSPACE))
    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).performClick()
    runBlocking { workspace.writeText("a.py", "someone else got here first") }
    compose.onNodeWithText(REPLACE_ALL_CONFIRM).performClick()
    scrollTo(hasText("a.py has changed in the meantime"))

    compose.onNodeWithText(UNDO_THE_REPLACE).assertDoesNotExist()
  }

  /**
   * A new walk clears the report - `showEmptyStage()` calls `clearMessage()` (`searchView.ts:1996`) -
   * and the undo goes with it, which is the whole of its lifetime. One undo, living exactly as long
   * as the line that offers it.
   */
  @Test
  fun anotherSearchTakesTheUndoAway() {
    show("a.py" to "cat = 1")

    searchAndReplace("cat", "dog", until = hasText("cat = 1"))
    scrollTo(hasText(REPLACE_ALL_IN_WORKSPACE))
    compose.onNodeWithText(REPLACE_ALL_IN_WORKSPACE).performClick()
    compose.onNodeWithText(REPLACE_ALL_CONFIRM).performClick()
    scrollTo(hasText(UNDO_THE_REPLACE))
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performTextInput("s")
    compose.waitUntil(UNDO_GONE_MILLIS) {
      compose.onAllNodes(hasText(UNDO_THE_REPLACE)).fetchSemanticsNodes().isEmpty()
    }

    assertEquals("dog = 1", textOf("a.py"))
  }
}

/** Longer than the 300 ms debounce the new walk waits out before it clears anything. */
private const val UNDO_GONE_MILLIS = 5_000L
