package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.SidePanel
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.domain.port.WorkspaceSearcher
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.assertReservesMinimumTouchTarget
import com.jupydroid.app.ui.editor.IGNORE_CASE
import com.jupydroid.app.ui.editor.MATCH_CASE
import com.jupydroid.app.ui.editor.MATCH_WHOLE_WORD
import com.jupydroid.app.ui.editor.USE_REGULAR_EXPRESSION
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `workbench.view.search` - the box, what the walk found grouped by the file it was found in, and
 * the message above it.
 *
 * VS Code answers for both halves of this panel, JupyterLab registering no workspace search at all
 * (`docs/DECISIONS.md`, "The activity bar's order takes both references").
 */
@RunWith(AndroidJUnit4::class)
class SearchSidePanelTest {
  @get:Rule val compose = createComposeRule()

  private val opened = mutableListOf<Pair<String, Int>>()

  private fun show(searcher: WorkspaceSearcher) {
    compose.setContent {
      JupyterDroidTheme {
        SearchSidePanel(
          searcher = searcher,
          onOpenMatch = { file, match -> opened += file.path to match.line },
        )
      }
    }
  }

  /**
   * Types into the box and waits for [until] to appear.
   *
   * The wait is real time rather than a virtual clock: the debounce runs on the main looper here,
   * so the 300 ms of `search.searchOnTypeDebouncePeriod` is 300 ms. What is waited *for* is named
   * by each test, because a helper that waited for "anything" would let a test pass on the wrong
   * thing appearing.
   */
  private fun searchFor(text: String, until: SemanticsMatcher) {
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performTextInput(text)
    compose.waitUntil(WALK_TIMEOUT_MILLIS) { compose.onAllNodes(until).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun thePanelNamesItself() {
    show(FakeWorkspaceSearcher())

    compose.onNodeWithText(SidePanel.SEARCH.label).assertIsDisplayed()
  }

  /**
   * **An empty query is the box and nothing else**, which is the reference rather than an omission:
   * VS Code's search view with nothing typed draws its input and no message at all. P2x-2 recorded
   * the same finding when it declined to invent a placeholder for the empty panel.
   */
  @Test
  fun anEmptyBoxIsTheBoxAndNothingElse() {
    show(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))

    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).assertIsDisplayed()
    compose.onNodeWithText(NO_RESULTS_FOUND).assertDoesNotExist()
  }

  @Test
  fun aMatchIsShownUnderTheFileItIsIn() {
    show(FakeWorkspaceSearcher(mapOf("notes.py" to "import os\nprint(cat)")))

    searchFor("cat", until = hasText("print(cat)"))

    compose.onNodeWithContentDescription(fileMatchLabel("notes.py", 1)).assertIsDisplayed()
    compose.onNodeWithText("print(cat)").assertIsDisplayed()
  }

  /** `buildResultCountMessage` - and its four arms, because "1 results in 1 files" is not English. */
  @Test
  fun theCountAboveTheTreeIsPluralisedTheReferencesFourWays() {
    show(FakeWorkspaceSearcher(mapOf("a.py" to "cat", "b.py" to "cat\ncat")))

    searchFor("cat", until = hasText(resultCountMessage(results = 3, files = 2)))

    compose.onNodeWithText(resultCountMessage(results = 3, files = 2)).assertIsDisplayed()
    assertEquals("1 result in 1 file", resultCountMessage(results = 1, files = 1))
    assertEquals("1 result in 2 files", resultCountMessage(results = 1, files = 2))
    assertEquals("2 results in 1 file", resultCountMessage(results = 2, files = 1))
  }

  /**
   * **The cap is drawn rather than silent**, which is what P2x-3 exists to fix: the walk stops at
   * `search.maxResults` and the reference says so above the tree (`searchView.ts:1897-1899`).
   */
  @Test
  fun aWalkThatStoppedEarlySaysSoAboveTheTree() {
    show(CappedWorkspaceWalk())

    searchFor("cat", until = hasText(RESULTS_ARE_A_SUBSET))

    compose.onNodeWithText(RESULTS_ARE_A_SUBSET).assertIsDisplayed()
  }

  /** An ordinary search draws no warning, or the warning would mean nothing when it appeared. */
  @Test
  fun anOrdinarySearchDrawsNoCapWarning() {
    show(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))

    searchFor("cat", until = hasText(resultCountMessage(results = 1, files = 1)))

    compose.onNodeWithText(RESULTS_ARE_A_SUBSET).assertDoesNotExist()
  }

  /** `noResultsFound` - said once the walk is over, and not while it is still running. */
  @Test
  fun aWalkThatFoundNothingSaysSo() {
    show(FakeWorkspaceSearcher(mapOf("notes.py" to "dog")))

    searchFor("cat", until = hasText(NO_RESULTS_FOUND))

    compose.onNodeWithText(NO_RESULTS_FOUND).assertIsDisplayed()
  }

  /** A tap on a match opens the file it is in, at the line it is on. */
  @Test
  fun tappingAMatchOpensTheFileItIsIn() {
    show(FakeWorkspaceSearcher(mapOf("notes.py" to "import os\nprint(cat)")))

    searchFor("cat", until = hasText("print(cat)"))
    compose.onNodeWithText("print(cat)").performClick()

    assertEquals(listOf("notes.py" to 2), opened)
  }

  /**
   * **Both rows reserve Material's 48dp**, which is the rule every tap target in this app follows -
   * and a result tree is the shape that makes *reserved* space rather than expanded space the thing
   * that matters. `MinimumTouchTarget`'s own doc says why: a clickable's hit area is expanded to
   * 48dp for free, but two neighbours in a `LazyColumn` both taking direct hits split the overlap,
   * and a file header sits directly above a match row.
   *
   * A match row is one line of `codeTextStyle` - 18sp - so left alone it reserves about 26dp.
   */
  @Test
  fun bothKindsOfResultRowReserveTheMinimumTouchTarget() {
    show(FakeWorkspaceSearcher(mapOf("notes.py" to "print(cat)")))

    searchFor("cat", until = hasText("print(cat)"))

    compose.onNodeWithContentDescription(fileMatchLabel("notes.py", 1)).assertReservesMinimumTouchTarget()
    compose.onNodeWithContentDescription(matchRowLabel(MatchedLine(line = 1, text = "print(cat)", range = 6..8)))
      .assertReservesMinimumTouchTarget()
  }

  /**
   * **The three toggles, in the reference's own order** - `caseSensitive`, `wholeWords`, `regex`,
   * which is the order `findInput.ts:181-197` appends them to the input's `.controls` under
   * `showCommonFindToggles: true` (`searchWidget.ts:454`). The same order the notebook's find bar
   * already lists them in.
   *
   * Order is asserted by **where they are**, not by where the semantics tree happens to walk: a
   * left-to-right row is what a reader sees, so left-to-right is what is measured.
   */
  @Test
  fun theThreeOptionTogglesSitOnTheBoxInTheReferencesOrder() {
    show(FakeWorkspaceSearcher())

    val lefts = listOf(MATCH_CASE, MATCH_WHOLE_WORD, USE_REGULAR_EXPRESSION).map { option ->
      compose.onNodeWithContentDescription(option).assertIsDisplayed().getUnclippedBoundsInRoot().left
    }

    assertEquals(lefts.sorted(), lefts)
  }

  /**
   * **A toggle says which way it is now by changing its name**, not by a check mark: Android's
   * accessibility tree keeps a toggled state on a node that carries no label, so a mark is a state
   * no flow and no screen reader can read (`docs/DECISIONS.md`). Every toggle in this app has two
   * labels for that reason, and these three already have theirs in `ui/editor/FindTopBar.kt`.
   */
  @Test
  fun aToggleNamesItselfByWhatItWillDoNext() {
    show(FakeWorkspaceSearcher())

    compose.onNodeWithContentDescription(MATCH_CASE).performClick()

    compose.onNodeWithContentDescription(IGNORE_CASE).assertIsDisplayed()
    compose.onNodeWithContentDescription(MATCH_CASE).assertDoesNotExist()
  }

  /** The toggle is read by the walk, not just carried: Match Case leaves the upper-case file behind. */
  @Test
  fun matchCaseNarrowsWhatIsOnScreen() {
    show(FakeWorkspaceSearcher(mapOf("lower.py" to "print(cat)", "upper.py" to "print(CAT)")))

    searchFor("cat", until = hasText(resultCountMessage(results = 2, files = 2)))
    compose.onNodeWithContentDescription(MATCH_CASE).performClick()

    compose.waitUntil(WALK_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText(resultCountMessage(results = 1, files = 1))).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithContentDescription(fileMatchLabel("lower.py", 1)).assertIsDisplayed()
    compose.onNodeWithContentDescription(fileMatchLabel("upper.py", 1)).assertDoesNotExist()
  }

  /** Three targets a thumb has to hit, side by side - Material's minimum, reserved on each. */
  @Test
  fun everyOptionToggleReservesTheMinimumTouchTarget() {
    show(FakeWorkspaceSearcher())

    listOf(MATCH_CASE, MATCH_WHOLE_WORD, USE_REGULAR_EXPRESSION).forEach { option ->
      compose.onNodeWithContentDescription(option).assertReservesMinimumTouchTarget()
    }
  }

  /** `collapseResults: alwaysExpand` - a file arrives open, and its header is what folds it. */
  @Test
  fun aFileArrivesExpandedAndItsHeaderFoldsIt() {
    show(FakeWorkspaceSearcher(mapOf("notes.py" to "print(cat)")))

    searchFor("cat", until = hasText("print(cat)"))
    compose.onNodeWithText("print(cat)").assertIsDisplayed()

    compose.onNodeWithContentDescription(fileMatchLabel("notes.py", 1)).performClick()
    compose.onNodeWithText("print(cat)").assertDoesNotExist()
  }
}

/** Long enough for the 300 ms debounce plus a walk of an in-memory workspace, short enough to fail. */
private const val WALK_TIMEOUT_MILLIS = 5_000L

/** A walk that stopped early - `search.maxResults`, without twenty thousand lines of it. */
private class CappedWorkspaceWalk : WorkspaceSearcher {
  override fun search(query: SearchQuery, scope: SearchScope): Flow<SearchProgress> = flowOf(
    SearchProgress.Found(MatchedFile("many.py", listOf(MatchedLine(line = 1, text = "cat", range = 0..2)))),
    SearchProgress.Complete(limitHit = true),
  )
}
