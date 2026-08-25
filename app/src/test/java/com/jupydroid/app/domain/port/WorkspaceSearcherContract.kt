package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [WorkspaceSearcher] must do, whoever implements it.
 *
 * VS Code's `ISearchService.textSearch` (`services/search/common/search.ts:195-240`): a result per
 * file, streamed rather than gathered, each carrying the line a match is on and where in that line
 * it is. The query's four options are `SearchQuery`'s and are already pinned in `SearchQueryTest`;
 * what is owed *here* is that a searcher actually applies them, that the two globs narrow what is
 * reached, and that the walk says when it is over - see [SearchProgress].
 *
 * Deliberately not in the contract: **what is skipped on the way**. A binary file, a `.ipynb` and a
 * directory symlink are things only an implementation with a filesystem behind it can meet, so
 * they are asked of the disk adapter.
 */
abstract class WorkspaceSearcherContract {
  protected abstract fun searcher(): WorkspaceSearcher

  /** The files the workspace holds for a test, as path to content. */
  protected abstract fun given(vararg files: Pair<String, String>)

  /** The files a query found, with the completion the flow ends on dropped - see [SearchProgress]. */
  private suspend fun WorkspaceSearcher.found(text: String, scope: SearchScope = SearchScope()): List<MatchedFile> =
    found(SearchQuery(text), scope)

  private suspend fun WorkspaceSearcher.found(
    query: SearchQuery,
    scope: SearchScope = SearchScope(),
  ): List<MatchedFile> = search(query, scope).toList().filterIsInstance<SearchProgress.Found>().map { it.file }

  @Test
  fun findsAMatchWithTheLineItIsOnAndWhereInIt() = runTest {
    given("notes.py" to "import os\nprint(os.getcwd())\n")

    val found = searcher().found("os").single()

    assertEquals("notes.py", found.path)
    // `ISearchRange.startLineNumber` is one-based, and `previewText` is the line itself.
    assertEquals(listOf(1, 2), found.matches.map { it.line })
    assertEquals("import os", found.matches.first().text)
    assertEquals(7..8, found.matches.first().range)
  }

  /** Two on one line are two matches, not one - each has its own range within the same text. */
  @Test
  fun twoMatchesOnOneLineAreTwoMatches() = runTest {
    given("notes.py" to "cat and cat")

    val matches = searcher().found("cat").single().matches

    assertEquals(2, matches.size)
    assertEquals(listOf(0..2, 8..10), matches.map { it.range })
    assertTrue(matches.all { it.line == 1 })
  }

  /** A file with nothing in it is not a result - `IFileMatch` exists because there was a match. */
  @Test
  fun aFileWithNoMatchIsNotEmitted() = runTest {
    given("notes.py" to "import os", "other.py" to "print(1)")

    assertEquals(listOf("notes.py"), searcher().found("os").map { it.path })
  }

  /** One item per file, streamed - the reference's per-file progress item. */
  @Test
  fun everyFileWithAMatchIsItsOwnResult() = runTest {
    given("a.py" to "cat", "b.py" to "cat")

    assertEquals(listOf("a.py", "b.py"), searcher().found("cat").map { it.path })
  }

  /** Workspace order, so what arrives first is what the browser would list first. */
  @Test
  fun resultsArriveInWorkspaceOrder() = runTest {
    given("b.py" to "cat", "a.py" to "cat")

    assertEquals(listOf("a.py", "b.py"), searcher().found("cat").map { it.path })
  }

  /** `CompiledQuery.Empty` - a blank box searches for nothing rather than for everything. */
  @Test
  fun anEmptyQueryFindsNothing() = runTest {
    given("notes.py" to "cat")

    assertTrue(searcher().found("").isEmpty())
  }

  /** `caseSensitive` is off when the box opens, as `_caseSensitive = false` is. */
  @Test
  fun caseIsIgnoredUnlessAsked() = runTest {
    given("notes.py" to "Cat")

    assertEquals(1, searcher().found(SearchQuery("cat")).size)
    assertTrue(searcher().found(SearchQuery("cat", caseSensitive = true)).isEmpty())
  }

  /** `wholeWords` wraps the escaped text in `\b`, so `cat` no longer finds `concatenate`. */
  @Test
  fun wholeWordsBoundsTheMatch() = runTest {
    given("notes.py" to "concatenate")

    assertTrue(searcher().found(SearchQuery("cat", wholeWords = true)).isEmpty())
  }

  /** `useRegex` decides whether the text is a pattern or the characters it is made of. */
  @Test
  fun aPatternIsOnlyAPatternWhenAsked() = runTest {
    given("notes.py" to "c.t")

    assertEquals(1, searcher().found(SearchQuery("c.t")).size)
    assertTrue(searcher().found(SearchQuery("c!t", useRegex = true)).isEmpty())
    assertEquals(1, searcher().found(SearchQuery("c[.]t", useRegex = true)).size)
  }

  /** The include box narrows what is reached - see `SearchFilter`. */
  @Test
  fun theIncludeGlobNarrowsWhatIsSearched() = runTest {
    given("notes.py" to "cat", "notes.txt" to "cat")

    val found = searcher().found("cat", SearchScope(include = "*.py"))

    assertEquals(listOf("notes.py"), found.map { it.path })
  }

  /** The exclude box removes what it names, and beats an include that also names it. */
  @Test
  fun theExcludeGlobRemovesWhatItNames() = runTest {
    given("notes.py" to "cat", "reports/notes.py" to "cat")

    val found = searcher().found("cat", SearchScope(exclude = "reports"))

    assertEquals(listOf("notes.py"), found.map { it.path })
  }

  /**
   * **Every search ends with exactly one [SearchProgress.Complete], and it is the last thing
   * emitted** - `ISearchService.textSearch` streams `ISearchProgressItem`s and *resolves* to one
   * `ISearchComplete` (`services/search/common/search.ts:595-607`).
   *
   * The invariant is what lets a caller know a walk is over rather than merely quiet, and it is
   * what carries [SearchProgress.Complete.limitHit] - which nothing else can answer, since only the
   * thing doing the walking knows it stopped early.
   */
  @Test
  fun aSearchEndsWithExactlyOneCompletion() = runTest {
    given("a.py" to "cat", "b.py" to "cat")

    val progress = searcher().search(SearchQuery("cat"), SearchScope()).toList()

    assertEquals(1, progress.count { it is SearchProgress.Complete })
    assertTrue(progress.last() is SearchProgress.Complete)
  }

  /**
   * A search that found nothing still completes, which is the half a caller would otherwise have to
   * guess at: an empty flow and a finished walk over an empty workspace look identical without it.
   */
  @Test
  fun aSearchThatFoundNothingStillCompletes() = runTest {
    given("notes.py" to "dog")

    val progress = searcher().search(SearchQuery("cat"), SearchScope()).toList()

    assertEquals(listOf(SearchProgress.Complete(limitHit = false)), progress)
  }

  /** `CompiledQuery.Empty` searches for nothing - and having searched for nothing, it completes. */
  @Test
  fun anEmptyQueryCompletesWithoutFindingAnything() = runTest {
    given("notes.py" to "cat")

    val progress = searcher().search(SearchQuery(""), SearchScope()).toList()

    assertEquals(listOf(SearchProgress.Complete(limitHit = false)), progress)
  }

  /** Nothing was left out, so nothing claims it was - see [SearchProgress.Complete.limitHit]. */
  @Test
  fun anOrdinarySearchDidNotHitTheLimit() = runTest {
    given("notes.py" to "cat")

    val completion = searcher().search(SearchQuery("cat"), SearchScope()).toList().last()

    assertEquals(SearchProgress.Complete(limitHit = false), completion)
  }
}
