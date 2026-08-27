package com.jupydroid.app.ui.shell

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.domain.port.WorkspaceSearcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the search panel is asking of the workspace, and what came back.
 *
 * `SearchView`'s half of `searchView.ts` with the tree left out: the box is read on a delay, the
 * walk it starts replaces the one before it, and the completion says whether what is on screen is
 * everything. VS Code answers both halves of this - JupyterLab registers no workspace search at
 * all (`docs/DECISIONS.md`, "The activity bar's order takes both references").
 */
class WorkspaceSearchViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun searchingIn(vararg files: Pair<String, String>, searcher: WorkspaceSearcher? = null) =
    WorkspaceSearchViewModel(searcher ?: FakeWorkspaceSearcher(files.toMap()))

  /** The box has to echo a keystroke at once; only the *walk* it causes is held back. */
  @Test
  fun theBoxShowsWhatWasTypedBeforeAnythingIsSearched() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.queryChanged(SearchQuery("cat"))

    assertEquals("cat", model.uiState.value.query.text)
    assertTrue(model.uiState.value.results.isEmpty())
  }

  /** `search.searchOnType` is on by default, so stopping typing is what runs the search. */
  @Test
  fun typingStopsAndTheWorkspaceIsSearched() = runTest {
    val model = searchingIn("notes.py" to "cat", "other.py" to "dog")

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    assertEquals(listOf("notes.py"), model.uiState.value.results.map { it.path })
    assertEquals(listOf(1), model.uiState.value.results.single().matches.map { it.line })
  }

  /**
   * **A burst of typing is one walk**, which is the whole of `search.searchOnTypeDebouncePeriod`:
   * a workspace walk per character would read the tree once per keystroke.
   */
  @Test
  fun aBurstOfTypingIsOneWalkAndNotOnePerCharacter() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)

    listOf("c", "ca", "cat").forEach { model.queryChanged(SearchQuery(it)) }
    advanceUntilIdle()

    assertEquals(listOf("cat"), walk.queries.map { it.text })
  }

  /** Nothing is searched until the period is up - measured *at* the boundary, not around it. */
  @Test
  fun nothingIsWalkedBeforeTheDebouncePeriodIsUp() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)

    model.queryChanged(SearchQuery("cat"))
    advanceTimeBy(SEARCH_ON_TYPE_DEBOUNCE_MILLIS)
    assertTrue(walk.queries.isEmpty())

    advanceTimeBy(1)
    assertEquals(listOf("cat"), walk.queries.map { it.text })
  }

  /**
   * **An option is walked at once, where a keystroke waits** - and that split is the reference's
   * own: `onDidOptionChange` calls `triggerQueryChange()` bare (`searchView.ts:712-713`), whose
   * default is `delay: 0` (`:1590`), while typing is the one caller that passes
   * `delay: searchOnTypeDebouncePeriod` (`:584`). A toggle is a decision already made; there is no
   * next character coming to wait for.
   *
   * The counterpart is [nothingIsWalkedBeforeTheDebouncePeriodIsUp], which measures the other arm
   * at its own boundary.
   */
  @Test
  fun anOptionIsWalkedAtOnceWhereAKeystrokeWaits() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)
    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    model.optionChanged(SearchQuery("cat", caseSensitive = true))
    runCurrent()

    // No virtual time was advanced between the toggle and this line, so a walk that is here at all
    // is one that did not wait - and the flag it carries is what says the option reached it.
    assertEquals(listOf(false, true), walk.queries.map { it.caseSensitive })
    assertTrue(model.uiState.value.query.caseSensitive)
  }

  /** The option is not just carried, it is *read* - Match Case leaves the upper-case file behind. */
  @Test
  fun matchCaseNarrowsWhatTheWalkFinds() = runTest {
    val model = searchingIn("lower.py" to "cat", "upper.py" to "CAT")

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()
    assertEquals(listOf("lower.py", "upper.py"), model.uiState.value.results.map { it.path })

    model.optionChanged(SearchQuery("cat", caseSensitive = true))
    advanceUntilIdle()
    assertEquals(listOf("lower.py"), model.uiState.value.results.map { it.path })
  }

  /**
   * A toggle flipped over a blank box walks nothing - the same `contentPattern.length === 0` guard
   * a keystroke meets (`searchView.ts:1631-1632`), which the fast path must not go round.
   */
  @Test
  fun anOptionFlippedOverABlankBoxWalksNothing() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)

    model.optionChanged(SearchQuery("", caseSensitive = true))
    advanceUntilIdle()

    assertTrue(walk.queries.isEmpty())
  }

  /**
   * **The two glob boxes reach the walk**, which is the whole of what `SearchScope` is for: the
   * reference folds `filesToInclude` / `filesToExclude` into the query before anything is walked
   * (`queryBuilder.ts:140-160`), and until now the panel passed a blank one every time.
   */
  @Test
  fun theScopeTypedIntoTheGlobBoxesReachesTheWalk() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)
    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    model.scopeChanged(SearchScope(include = "*.py", exclude = "reports"))
    advanceUntilIdle()

    assertEquals(listOf(SearchScope(), SearchScope(include = "*.py", exclude = "reports")), walk.scopes)
  }

  /**
   * **A glob box is typed into, so it waits like the box above it** - and this is the one place the
   * reference does *not* use its zero delay: `onFilePatternSubmit` passes
   * `delay: searchOnTypeDebouncePeriod` (`searchView.ts:582-588`), fired per keystroke by
   * `inputBox.onDidChange` (`patternInputWidget.ts:156`). A toggle is a decision; a glob is typing.
   */
  @Test
  fun aBurstOfGlobTypingIsOneWalkAndNotOnePerCharacter() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)
    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    listOf("*", "*.", "*.py").forEach { model.scopeChanged(SearchScope(include = it)) }
    advanceTimeBy(SEARCH_ON_TYPE_DEBOUNCE_MILLIS)
    assertEquals(listOf(SearchScope()), walk.scopes)

    advanceTimeBy(1)
    assertEquals(listOf(SearchScope(), SearchScope(include = "*.py")), walk.scopes)
  }

  /** A glob narrows what the walk reaches, which is the half a recorded argument does not prove. */
  @Test
  fun anExcludeLeavesItsFolderOutOfTheResults() = runTest {
    val model = searchingIn("notes.py" to "cat", "reports/analysis.py" to "cat")

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()
    assertEquals(listOf("notes.py", "reports/analysis.py"), model.uiState.value.results.map { it.path })

    model.scopeChanged(SearchScope(exclude = "reports"))
    advanceUntilIdle()
    assertEquals(listOf("notes.py"), model.uiState.value.results.map { it.path })
  }

  /** The same blank-box guard a keystroke meets - a glob typed over nothing walks nothing. */
  @Test
  fun aGlobTypedOverABlankBoxWalksNothing() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)

    model.scopeChanged(SearchScope(include = "*.py"))
    advanceUntilIdle()

    assertTrue(walk.scopes.isEmpty())
  }

  /** The scope is state, not an argument: the next keystroke carries the globs already set. */
  @Test
  fun theScopeStandsWhileTheQueryChangesUnderIt() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)
    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()
    model.scopeChanged(SearchScope(include = "*.py"))
    advanceUntilIdle()

    model.queryChanged(SearchQuery("cats"))
    advanceUntilIdle()

    assertEquals(SearchScope(include = "*.py"), walk.scopes.last())
    assertEquals("cats", walk.queries.last().text)
  }

  /**
   * **The `…` starts closed and stays where it was put** - `queryDetailsExpanded`
   * (`searchView.ts:500`), which the reference keeps in its viewlet state rather than in the DOM.
   * Here that is the ViewModel, which is what survives the panel being switched away from.
   */
  @Test
  fun theGlobBoxesAreHiddenUntilTheyAreAskedFor() = runTest {
    val model = searchingIn("notes.py" to "cat")

    assertFalse(model.uiState.value.scopeShown)
    model.toggleScopeShown()
    assertTrue(model.uiState.value.scopeShown)
    model.toggleScopeShown()
    assertFalse(model.uiState.value.scopeShown)
  }

  /**
   * **A superseded walk is cancelled, not merged**, which is what a `Delayer` plus a cancellation
   * token buys the reference - and what a result list would otherwise get wrong in the worst way,
   * by showing one query's hits under another query's text.
   */
  @Test
  fun aSupersededWalkIsCancelledRatherThanMerged() = runTest {
    val slow = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("cat.py" to "cat", "dog.py" to "dog")), millis = 5_000)
    val model = searchingIn(searcher = slow)

    model.queryChanged(SearchQuery("cat"))
    advanceTimeBy(SEARCH_ON_TYPE_DEBOUNCE_MILLIS + 1_000)
    model.queryChanged(SearchQuery("dog"))
    advanceUntilIdle()

    // **Both halves, because the first alone is not the claim.** A walk left to run to completion
    // also ends with the newer query's results on screen, since each walk empties the tree as it
    // starts - measured, by mutating `collectLatest` to `collect` and watching this pass. What says
    // it was *dropped* is that it never reached the end of its own flow.
    assertEquals(listOf("dog"), slow.finished.map { it.text })
    assertEquals(listOf("dog.py"), model.uiState.value.results.map { it.path })
  }

  /** One item per file, in the order the walk found them - the port's own promise, kept. */
  @Test
  fun resultsAccumulateInTheOrderTheyWereFound() = runTest {
    val model = searchingIn("b.py" to "cat", "a.py" to "cat")

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    assertEquals(listOf("a.py", "b.py"), model.uiState.value.results.map { it.path })
  }

  /**
   * The cap the walk reported reaches the state, because the panel has to draw it
   * (`searchView.ts:1897-1899`). Nothing here counts to 20000 - only the walk can answer it.
   */
  @Test
  fun theCapTheWalkReportedReachesTheState() = runTest {
    val model = searchingIn(searcher = CappedWorkspaceWalk())

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    assertTrue(model.uiState.value.limitHit)
  }

  /** An ordinary search says nothing was left out, so the panel draws no warning. */
  @Test
  fun anOrdinarySearchLeavesTheCapUnclaimed() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    assertFalse(model.uiState.value.limitHit)
  }

  /**
   * `if (contentPattern.length === 0) { this.clearSearchResults(false); return; }`
   *
   * **The workspace is not walked for a blank box**, which is the half an emptied result list does
   * not prove: a walk for the empty query finds nothing either, so both arrangements leave the same
   * screen behind. Measured - stubbing the guard out left this green until the walk itself was
   * counted.
   */
  @Test
  fun emptyingTheBoxClearsWhatWasFoundWithoutWalkingForIt() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()
    model.queryChanged(SearchQuery(""))
    advanceUntilIdle()

    assertTrue(model.uiState.value.results.isEmpty())
    assertFalse(model.uiState.value.limitHit)
    assertEquals(listOf("cat"), walk.queries.map { it.text })
  }

  /**
   * **A half-typed pattern says so and leaves what was found alone**, which is the reference's own
   * `if (!this.searchWidget.searchInput?.inputBox.isInputValid()) { return; }`
   * (`searchView.ts:1612-1614`): the box carries the complaint and the tree is not emptied under a
   * user who is mid-keystroke.
   */
  @Test
  fun aPatternThatDoesNotParseSaysSoAndKeepsWhatWasFound() = runTest {
    val walk = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("notes.py" to "cat")))
    val model = searchingIn(searcher = walk)

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()
    model.queryChanged(SearchQuery("cat[", useRegex = true))
    advanceUntilIdle()

    assertTrue(model.uiState.value.parseError!!.contains("cat["))
    assertEquals(listOf("notes.py"), model.uiState.value.results.map { it.path })
    assertEquals(listOf("cat"), walk.queries.map { it.text })
  }

  /** The complaint goes as soon as the pattern parses again - a stale error is a wrong one. */
  @Test
  fun aPatternThatParsesAgainTakesTheComplaintBack() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.queryChanged(SearchQuery("cat[", useRegex = true))
    model.queryChanged(SearchQuery("cat[.]", useRegex = true))

    assertNull(model.uiState.value.parseError)
  }

  /** A walk in flight is something the panel shows, and a finished one is not. */
  @Test
  fun theWalkIsReportedWhileItRunsAndNotAfterIt() = runTest {
    val model = searchingIn(searcher = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("a.py" to "cat")), millis = 5_000))

    model.queryChanged(SearchQuery("cat"))
    advanceTimeBy(SEARCH_ON_TYPE_DEBOUNCE_MILLIS + 1_000)
    assertTrue(model.uiState.value.searching)

    advanceUntilIdle()
    assertFalse(model.uiState.value.searching)
  }

  /**
   * **A walk has to have *finished* before the panel says anything about what it found**, and this
   * is the flag that says so - `showEmptyStage()` at the start of a walk, the messages added in
   * `onSearchComplete` (`searchView.ts:1900-1905`). Without it a box holding "cat" and no results
   * yet would read "No results found" for the length of the debounce.
   */
  @Test
  fun nothingIsSettledUntilAWalkHasFinished() = runTest {
    val model = searchingIn(searcher = SlowWorkspaceWalk(FakeWorkspaceSearcher(mapOf("a.py" to "cat")), millis = 5_000))

    model.queryChanged(SearchQuery("cat"))
    assertFalse(model.uiState.value.completed)
    advanceTimeBy(SEARCH_ON_TYPE_DEBOUNCE_MILLIS + 1_000)
    assertFalse(model.uiState.value.completed)

    advanceUntilIdle()
    assertTrue(model.uiState.value.completed)
  }

  /**
   * **What was found last stays settled while the next query is being typed**, which is the whole
   * of `showEmptyStage()` being called when a walk *starts* rather than when the box changes: a
   * result list that emptied on every keystroke would flicker under a thumb.
   */
  @Test
  fun theLastAnswerStandsWhileTheNextQueryIsBeingTyped() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()
    model.queryChanged(SearchQuery("catn"))

    assertTrue(model.uiState.value.completed)
    assertEquals(listOf("notes.py"), model.uiState.value.results.map { it.path })
  }

  /** An emptied box has nothing settled to say, so the panel goes back to its box and nothing else. */
  @Test
  fun anEmptiedBoxHasNothingSettledToSay() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()
    model.queryChanged(SearchQuery(""))
    advanceUntilIdle()

    assertFalse(model.uiState.value.completed)
  }

  /** Nothing is walked for a blank box, so nothing claims to be walking either. */
  @Test
  fun anEmptyBoxIsNotAWalkInFlight() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.queryChanged(SearchQuery(""))
    advanceUntilIdle()

    assertFalse(model.uiState.value.searching)
  }

  /**
   * `.replace-container.disabled` - the replace row starts collapsed and the chevron opens it
   * (`searchWidget.ts:553`, `onToggleReplaceButton`).
   */
  @Test
  fun theReplaceRowIsHiddenUntilItIsAskedFor() = runTest {
    val model = searchingIn("notes.py" to "cat")

    assertFalse(model.uiState.value.replaceShown)
    model.toggleReplaceShown()
    assertTrue(model.uiState.value.replaceShown)
    model.toggleReplaceShown()
    assertFalse(model.uiState.value.replaceShown)
  }

  /** What is typed into the replace box changes nothing that is *found* - only what a replace writes. */
  @Test
  fun theReplacementIsRememberedAndChangesNothingThatWasFound() = runTest {
    val model = searchingIn("notes.py" to "cat")
    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    model.replaceTextChanged("dog")

    assertEquals("dog", model.uiState.value.replaceText)
    assertEquals(listOf("notes.py"), model.uiState.value.results.map { it.path })
  }

  /**
   * `preserveCase` lives on the **replace** input, not the search one - `ReplaceInput`'s own toggle
   * (`searchWidget.ts:570-576`), which is why it is not a fourth `SearchQuery` flag.
   */
  @Test
  fun preserveCaseIsFlippedOnTheReplaceRowAndNotOnTheQuery() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.togglePreserveCase()

    assertTrue(model.uiState.value.preserveCase)
    assertFalse(model.uiState.value.query.caseSensitive)
  }
}

/** A walk that takes time, and remembers what it was asked - the two halves a debounce is tested by. */
private class SlowWorkspaceWalk(private val walk: WorkspaceSearcher, private val millis: Long = 0) :
  WorkspaceSearcher {
  /** What was asked for. Recorded *before* the delay, so it means "asked" and not "answered". */
  val queries = mutableListOf<SearchQuery>()

  /** Which files each walk was told to reach - the half `queries` cannot show. */
  val scopes = mutableListOf<SearchScope>()

  /** What ran to the end of its own flow. A cancelled walk never gets here, which is the point. */
  val finished = mutableListOf<SearchQuery>()

  override fun search(query: SearchQuery, scope: SearchScope): Flow<SearchProgress> = flow {
    queries += query
    scopes += scope
    delay(millis)
    emitAll(walk.search(query, scope))
    finished += query
  }
}

/** A walk that stopped early - `search.maxResults`, without twenty thousand lines of it. */
private class CappedWorkspaceWalk : WorkspaceSearcher {
  override fun search(query: SearchQuery, scope: SearchScope): Flow<SearchProgress> = flowOf(
    SearchProgress.Found(MatchedFile("many.py", listOf(MatchedLine(line = 1, text = "cat", range = 0..2)))),
    SearchProgress.Complete(limitHit = true),
  )
}
