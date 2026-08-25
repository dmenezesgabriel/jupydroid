package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.domain.port.WorkspaceSearcher
import com.jupydroid.app.domain.port.WorkspaceSearcherContract
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/** The disk searcher owes the port's contract, plus what only a real filesystem can be asked. */
class DiskWorkspaceSearcherTest : WorkspaceSearcherContract() {
  @get:Rule val temp = TemporaryFolder()

  override fun searcher(): WorkspaceSearcher = DiskWorkspaceSearcher(temp.root)

  override fun given(vararg files: Pair<String, String>) {
    files.forEach { (path, content) ->
      File(temp.root, path).apply { parentFile?.mkdirs() }.writeText(content)
    }
  }

  private suspend fun found(text: String) =
    progressFor(text).filterIsInstance<SearchProgress.Found>().map { it.file.path }

  private suspend fun progressFor(text: String, searcher: WorkspaceSearcher = searcher()): List<SearchProgress> =
    searcher.search(SearchQuery(text), SearchScope()).toList()

  /** Directories first then natural name order, at every level - the browser's own order. */
  @Test
  fun theWalkIsInWorkspaceOrder() = runTest {
    given("notes.py" to "cat", "reports/b.py" to "cat", "reports/a.py" to "cat", "alpha.py" to "cat")

    assertEquals(listOf("reports/a.py", "reports/b.py", "alpha.py", "notes.py"), found("cat"))
  }

  /**
   * Hidden entries are not searched, because they are not listed either.
   *
   * `ContentsManager.allow_hidden` is false by default, so the browser does not show them and a
   * result naming one would be a result nobody could open.
   */
  @Test
  fun hiddenEntriesAreNotSearched() = runTest {
    given(".secret" to "cat", ".config/notes.py" to "cat", "notes.py" to "cat")

    assertEquals(listOf("notes.py"), found("cat"))
  }

  /**
   * **A binary file is skipped**, by ripgrep's own rule: *"a file is considered 'binary' if and
   * only if it contains a NUL byte somewhere in its contents"* (ripgrep `GUIDE.md`, "Binary data"),
   * and its default mode - the one that applies to a recursive walk - removes such a file from the
   * search completely. VS Code states no rule of its own; it delegates the whole of this.
   */
  @Test
  fun aBinaryFileIsSkipped() = runTest {
    given("notes.py" to "cat")
    File(temp.root, "image.png").writeBytes("cat".toByteArray() + 0 + "cat".toByteArray())

    assertEquals(listOf("notes.py"), found("cat"))
  }

  /**
   * **A notebook is not searched yet, and that is deliberate.** Read as the JSON it is, `cell_type`
   * and a base64 image output are both text and both would be reported - which is exactly what
   * P2x-4's gate forbids. Skipping ships no wrong answer; P2x-4 lifts the skip and makes a hit a
   * *cell* hit, as `searchNotebookHelpers.ts` does.
   */
  @Test
  fun aNotebookIsNotSearchedUntilAHitCanNameItsCell() = runTest {
    given("summary.ipynb" to """{"cells":[{"cell_type":"code","source":["cat"]}]}""", "notes.py" to "cat")

    assertEquals(listOf("notes.py"), found("cat"))
    // The half that matters: nothing in the workspace holds `cell_type` except the notebook's JSON.
    assertTrue(found("cell_type").isEmpty())
  }

  /**
   * **A directory symlink is not descended into**, where `search.followSymlinks` defaults to true.
   *
   * The workspace is inside a rootfs and a cell can call `os.symlink`, so a loop is reachable;
   * ripgrep detects one and this app has nothing that would. Recorded as a departure in
   * `docs/DECISIONS.md`.
   */
  @Test
  fun aDirectorySymlinkIsNotFollowed() = runTest {
    given("real/inside.py" to "cat")
    Files.createSymbolicLink(File(temp.root, "link").toPath(), File(temp.root, "real").toPath())

    assertEquals(listOf("real/inside.py"), found("cat"))
  }

  /** A loop cannot hang the walk, which is the whole reason the rule above exists. */
  @Test
  fun aSymlinkLoopDoesNotHangTheWalk() = runTest {
    given("deep/notes.py" to "cat")
    Files.createSymbolicLink(File(temp.root, "deep/up").toPath(), temp.root.toPath())

    assertEquals(listOf("deep/notes.py"), found("cat"))
  }

  /** A dangling link names nothing to read, and is passed over rather than thrown on. */
  @Test
  fun aDanglingSymlinkIsPassedOver() = runTest {
    given("notes.py" to "cat")
    Files.createSymbolicLink(File(temp.root, "gone.py").toPath(), File(temp.root, "nowhere.py").toPath())

    assertEquals(listOf("notes.py"), found("cat"))
  }

  /** `search.maxResults`' registered default, which is the only thing bounding a phone's walk. */
  @Test
  fun theWalkStopsAtTheReferencesResultCap() = runTest {
    given("many.py" to "cat\n".repeat(MAX_SEARCH_RESULTS + 10))

    val matches = progressFor("cat").filterIsInstance<SearchProgress.Found>().sumOf { it.file.matches.size }

    assertEquals(MAX_SEARCH_RESULTS, matches)
  }

  /**
   * **A truncated file says so**, which is the whole of what `limitHit` is for: the walk stopped
   * inside `many.py`, so what was reported is a subset and the panel has to draw the reference's
   * own warning above it (`searchView.ts:1897-1899`).
   *
   * Driven through the cap rather than through 20000 matching lines, because `search.maxResults`
   * **is a setting** in the reference and is read per query (`searchView.ts:1670`) - so a small one
   * is an ordinary configuration and not a test hook.
   */
  @Test
  fun aWalkTruncatedInsideAFileSaysTheLimitWasHit() = runTest {
    given("many.py" to "cat\ncat\ncat\n")

    val progress = progressFor("cat", DiskWorkspaceSearcher(temp.root, maxResults = 2))

    assertEquals(listOf(1, 2), progress.filterIsInstance<SearchProgress.Found>().single().file.matches.map { it.line })
    assertEquals(SearchProgress.Complete(limitHit = true), progress.last())
  }

  /**
   * A cap that lands **exactly** on the last match left nothing out, and does not claim it did.
   *
   * The seam a running count in the caller could not see, and the reason `limitHit` is the port's
   * to answer: two matches reported against a cap of two is either "all of them" or "the first two
   * of many", and only the thing walking the tree knows which.
   */
  @Test
  fun aCapThatLandsExactlyOnTheLastMatchDidNotTruncate() = runTest {
    given("some.py" to "cat\ncat\n")

    val progress = progressFor("cat", DiskWorkspaceSearcher(temp.root, maxResults = 2))

    assertEquals(SearchProgress.Complete(limitHit = false), progress.last())
  }

  /** A file the budget could not be spent on at all is a subset too - the walk stopped before it. */
  @Test
  fun aFileTheWalkNeverReachedSaysTheLimitWasHit() = runTest {
    given("a.py" to "cat\ncat\n", "b.py" to "cat")

    val progress = progressFor("cat", DiskWorkspaceSearcher(temp.root, maxResults = 2))

    assertEquals(listOf("a.py"), progress.filterIsInstance<SearchProgress.Found>().map { it.file.path })
    assertEquals(SearchProgress.Complete(limitHit = true), progress.last())
  }

  /** A path that leaves the workspace is refused by `WorkspaceRoot`, and nothing walks above it. */
  @Test
  fun nothingAboveTheRootIsReached() = runTest {
    File(temp.root.parentFile, "outside.py").writeText("cat")
    given("notes.py" to "cat")

    assertTrue(found("cat").none { it.contains("outside") })
  }
}
