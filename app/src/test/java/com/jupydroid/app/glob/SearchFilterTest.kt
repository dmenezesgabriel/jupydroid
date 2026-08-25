package com.jupydroid.app.glob

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which paths a search reaches, given what the two boxes hold.
 *
 * `QueryBuilder.commonQuery` folds the include box, the exclude box and the two settings into one
 * `IExpression` pair (`queryBuilder.ts:140-160`); this is that pair asked one path at a time.
 */
class SearchFilterTest {
  @Test
  fun nothingTypedSearchesEverythingButTheDefaults() {
    val filter = SearchFilter(include = "", exclude = "")

    assertTrue(filter.searches("notes.py"))
    assertTrue(filter.searches("reports/deep/summary.txt"))
    assertFalse(filter.searches("node_modules"))
  }

  /** An include **narrows**: with one typed, only what it names is searched. */
  @Test
  fun anIncludeNarrows() {
    val filter = SearchFilter(include = "*.py", exclude = "")

    assertTrue(filter.searches("notes.py"))
    assertTrue(filter.searches("reports/deep/notes.py"))
    assertFalse(filter.searches("notes.txt"))
  }

  /** An exclude **removes**, and it wins over an include that also names the path. */
  @Test
  fun anExcludeRemovesAndBeatsAnInclude() {
    val filter = SearchFilter(include = "*.py", exclude = "reports")

    assertTrue(filter.searches("notes.py"))
    assertFalse(filter.searches("reports/notes.py"))
  }

  /** The registered defaults apply whether or not the user typed an exclude of their own. */
  @Test
  fun theDefaultsApplyBesideWhatWasTyped() {
    val filter = SearchFilter(include = "", exclude = "*.log")

    assertFalse(filter.searches("build.log"))
    assertFalse(filter.searches("node_modules"))
    assertFalse(filter.searches("reports/Thumbs.db"))
  }

  /**
   * **An include does not prune the walk**, and an exclude does.
   *
   * A `.py` can be any distance below a directory, so a directory that does not itself match the
   * include still has to be descended into - which is why the walk asks a different question of a
   * directory than of a file.
   */
  @Test
  fun anIncludeDoesNotPruneTheWalkAndAnExcludeDoes() {
    val filter = SearchFilter(include = "*.py", exclude = "")

    assertTrue(filter.descendsInto("reports"))
    assertFalse(filter.searches("reports"))
  }

  /** Pruning is what makes a default exclude cover the subtree it names - see `DEFAULT_EXCLUDED_GLOBS`. */
  @Test
  fun anExcludedDirectoryIsNotDescendedInto() {
    val filter = SearchFilter(include = "", exclude = "reports")

    assertFalse(filter.descendsInto("node_modules"))
    assertFalse(filter.descendsInto("deep/reports"))
    assertTrue(filter.descendsInto("deep"))
  }
}
