package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The order a directory is listed in, which is JupyterLab's and not ours.
 *
 * `Private.sort` in `packages/filebrowser/src/listing.ts`:
 *
 * - **directories first**, always - `getPriority` returns 2 for a directory;
 * - notebooks next *only* when `sortNotebooksFirst` is on, and it is **off** by default;
 * - then by name with `Intl.Collator{ numeric: sortFileNamesNaturally }`, which defaults to
 *   **true** - so `file2` sorts before `file10`, not after it.
 *
 * Winlator's rootfs browser agrees on the first rule (`FileInfo.compareTo`: directories, then
 * name), which is the same answer arrived at independently for the same kind of tree.
 */
class WorkspaceOrderTest {
  @Test
  fun directoriesComeFirst() {
    val listed = listOf(file("beta.txt"), dir("alpha"), file("alpha.txt"), dir("zeta"))

    assertEquals(listOf("alpha", "zeta", "alpha.txt", "beta.txt"), listed.inWorkspaceOrder().map { it.name })
  }

  /** The default is natural order, so a run of numbered files reads the way a person counts. */
  @Test
  fun namesSortNaturallyRatherThanByCharacter() {
    val listed = listOf(file("file10.py"), file("file2.py"), file("file1.py"))

    assertEquals(listOf("file1.py", "file2.py", "file10.py"), listed.inWorkspaceOrder().map { it.name })
  }

  @Test
  fun theSameRuleAppliesInsideDirectories() {
    val listed = listOf(dir("run10"), dir("run2"))

    assertEquals(listOf("run2", "run10"), listed.inWorkspaceOrder().map { it.name })
  }

  /**
   * Notebooks are **not** hoisted above other files.
   *
   * `sortNotebooksFirst` exists in the reference and defaults to false, so a notebook sorts among
   * the files by name. Hoisting them would be a setting we invented and a difference nobody asked
   * for.
   */
  @Test
  fun notebooksAreNotHoistedAboveOtherFiles() {
    val listed = listOf(file("b.ipynb"), file("a.txt"))

    assertEquals(listOf("a.txt", "b.ipynb"), listed.inWorkspaceOrder().map { it.name })
  }

  @Test
  fun caseDoesNotSplitTheAlphabet() {
    val listed = listOf(file("Beta.py"), file("alpha.py"))

    assertEquals(listOf("alpha.py", "Beta.py"), listed.inWorkspaceOrder().map { it.name })
  }

  /**
   * `filebrowser:go-up` is one directory, and at the root there is nowhere above to go.
   *
   * The rule left `ExplorerViewModel` when detekt counted its thirteenth function: it is a pure
   * fact about a path, so it belongs beside the other path rules where it can be driven without a
   * ViewModel at all.
   */
  @Test
  fun theParentOfAPathIsTheDirectoryHoldingIt() {
    assertEquals("reports", "reports/deep".parentDirectory())
    assertEquals("reports/deep", "reports/deep/file.txt".parentDirectory())
  }

  /** The root is the empty string, so a top-level entry's parent is the root and not itself. */
  @Test
  fun anythingAtTheRootHasTheRootAsItsParent() {
    assertEquals("", "notes.txt".parentDirectory())
    assertEquals("", "".parentDirectory())
  }

  // The epoch for every entry, and identically so: this is about *order*, and the order is by
  // kind then name. A varying time here would let a sort that secretly used it still pass.
  private fun dir(name: String) =
    WorkspaceEntry(name, path = name, kind = WorkspaceEntryKind.DIRECTORY, updatedAt = Instant.EPOCH)

  private fun file(name: String) =
    WorkspaceEntry(name, path = name, kind = WorkspaceEntryKind.FILE, updatedAt = Instant.EPOCH)
}
