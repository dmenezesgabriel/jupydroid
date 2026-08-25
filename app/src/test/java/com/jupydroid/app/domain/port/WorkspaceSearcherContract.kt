package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.MatchedFile
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
 * reached, and that a replace writes what a search found.
 *
 * Deliberately not in the contract: **what is skipped on the way**. A binary file, a `.ipynb` and a
 * directory symlink are things only an implementation with a filesystem behind it can meet, so
 * they are asked of the disk adapter.
 */
abstract class WorkspaceSearcherContract {
  protected abstract fun searcher(): WorkspaceSearcher

  /** The files the workspace holds for a test, as path to content. */
  protected abstract fun given(vararg files: Pair<String, String>)

  private suspend fun WorkspaceSearcher.found(text: String, scope: SearchScope = SearchScope()): List<MatchedFile> =
    search(SearchQuery(text), scope).toList()

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

    assertEquals(1, searcher().search(SearchQuery("cat"), SearchScope()).toList().size)
    assertTrue(searcher().search(SearchQuery("cat", caseSensitive = true), SearchScope()).toList().isEmpty())
  }

  /** `wholeWords` wraps the escaped text in `\b`, so `cat` no longer finds `concatenate`. */
  @Test
  fun wholeWordsBoundsTheMatch() = runTest {
    given("notes.py" to "concatenate")

    assertTrue(searcher().search(SearchQuery("cat", wholeWords = true), SearchScope()).toList().isEmpty())
  }

  /** `useRegex` decides whether the text is a pattern or the characters it is made of. */
  @Test
  fun aPatternIsOnlyAPatternWhenAsked() = runTest {
    given("notes.py" to "c.t")

    assertEquals(1, searcher().search(SearchQuery("c.t"), SearchScope()).toList().size)
    assertTrue(searcher().search(SearchQuery("c!t", useRegex = true), SearchScope()).toList().isEmpty())
    assertEquals(1, searcher().search(SearchQuery("c[.]t", useRegex = true), SearchScope()).toList().size)
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
}
