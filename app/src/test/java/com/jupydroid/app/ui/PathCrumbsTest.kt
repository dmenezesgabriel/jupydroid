package com.jupydroid.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ancestry both the browser and the open document draw from - see `ui/PathCrumbs.kt`.
 *
 * The browser's half was covered only through `ExplorerViewModelTest`, which asserts the crumbs a
 * listing produced; these are the segmenting itself, which is now read by two surfaces.
 */
class PathCrumbsTest {
  @Test
  fun theRootIsOneCrumbWithNoPath() {
    assertEquals(listOf("" to ROOT_LABEL), crumbsFor("", ROOT_LABEL))
  }

  @Test
  fun everySegmentCarriesThePathThatReachesIt() {
    assertEquals(
      listOf("" to ROOT_LABEL, "reports" to "reports", "reports/q1" to "q1"),
      crumbsFor("reports/q1", ROOT_LABEL),
    )
  }

  /**
   * A document at the workspace root has no ancestry, and the row that would draw it is **absent**
   * rather than empty - the adaptation P2w-1 records. Empty is what says so.
   */
  @Test
  fun aDocumentAtTheRootHasNoAncestors() {
    assertEquals(emptyList<Pair<String, String>>(), ancestorCrumbsFor("At Root.ipynb", ROOT_LABEL))
  }

  /**
   * **The document's own name is never a crumb.** VS Code ends its trail with the file
   * (`breadcrumbsModel.ts:142`) because its breadcrumb sits under a tab bar; ours sits under a
   * title row that already carries the name, so every segment here is a folder.
   */
  @Test
  fun aDocumentInAFolderCrumbsToThatFolderAndNoFurther() {
    assertEquals(
      listOf("" to ROOT_LABEL, "reports" to "reports"),
      ancestorCrumbsFor("reports/Nested.ipynb", ROOT_LABEL),
    )
  }

  @Test
  fun everyAncestorOfANestedDocumentIsACrumb() {
    assertEquals(
      listOf("" to ROOT_LABEL, "reports" to "reports", "reports/q1" to "q1"),
      ancestorCrumbsFor("reports/q1/Sales.ipynb", ROOT_LABEL),
    )
  }

  /** A dotted folder name is a folder, not a suffix: the split is on `/` and nothing else. */
  @Test
  fun aFolderNameWithADotInItIsStillOneSegment() {
    assertEquals(
      listOf("" to ROOT_LABEL, "v1.2" to "v1.2"),
      ancestorCrumbsFor("v1.2/Notes.ipynb", ROOT_LABEL),
    )
  }
}
