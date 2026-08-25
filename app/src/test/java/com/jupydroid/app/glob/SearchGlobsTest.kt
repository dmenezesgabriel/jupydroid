package com.jupydroid.app.glob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the two include / exclude boxes mean, and what is excluded when nobody has typed anything.
 *
 * `QueryBuilder.parseSearchPaths` (`queryBuilder.ts:376-418`) for the boxes, and the two
 * registered defaults for the rest - `files.exclude` (`files.contribution.ts:156-158`) and
 * `search.exclude` (`search.contribution.ts:109`), which *"inherits all glob patterns from the
 * `files.exclude` setting"* (`:108`).
 */
class SearchGlobsTest {
  private fun included(box: String, path: String) = globsFrom(box).any { it.matches(path) }

  private fun excludedByDefault(path: String) = DEFAULT_EXCLUDED_GLOBS.any { it.matches(path) }

  /** `splitGlobPattern` - `splitGlobAware(pattern, ',')`, trimmed, empties dropped. */
  @Test
  fun theBoxIsACommaSeparatedListOfPatterns() {
    assertEquals(2, globsFrom("*.py, *.txt").size / EXPANSIONS_PER_PATTERN)
    assertEquals(0, globsFrom("").size)
    assertEquals(0, globsFrom("  ,  ").size)
  }

  /** A comma inside braces is not a separator - that is what "glob aware" means. */
  @Test
  fun aCommaInsideBracesDoesNotSplit() {
    assertEquals(EXPANSIONS_PER_PATTERN, globsFrom("*.{py,txt}").size)
  }

  /** `expandGlobalGlob`: a pattern is expanded to reach at any depth, so `notes.py` is found deep. */
  @Test
  fun aPatternMatchesAtEveryDepth() {
    assertTrue(included("*.py", "notes.py"))
    assertTrue(included("*.py", "reports/deep/notes.py"))
  }

  /** The trailing-globstar half of the expansion is what makes a folder name include its contents. */
  @Test
  fun afolderNameIncludesItsContents() {
    assertTrue(included("reports", "reports"))
    assertTrue(included("reports", "reports/notes.py"))
    assertTrue(included("reports", "deep/reports/notes.py"))
    assertFalse(included("reports", "notes.py"))
  }

  /** *"convert `.js` to `*.js`"* (`queryBuilder.ts:399-401`) - a bare extension is a pattern. */
  @Test
  fun aLeadingDotBecomesAStar() {
    assertTrue(included(".py", "notes.py"))
  }

  /** `rtrim(s, '/')` - a trailing separator is not part of the pattern. */
  @Test
  fun aTrailingSeparatorIsTrimmed() {
    assertTrue(included("reports/", "reports/notes.py"))
  }

  /** `search.exclude`'s three, verbatim. */
  @Test
  fun theSearchExcludeDefaultsAreExcluded() {
    assertTrue(excludedByDefault("node_modules"))
    assertTrue(excludedByDefault("reports/node_modules"))
    assertTrue(excludedByDefault("bower_components"))
    assertTrue(excludedByDefault("reports/everything.code-search"))
  }

  /** `files.exclude`'s five, which `search.exclude` inherits. */
  @Test
  fun theFilesExcludeDefaultsAreInherited() {
    assertTrue(excludedByDefault(".git"))
    assertTrue(excludedByDefault(".svn"))
    assertTrue(excludedByDefault(".hg"))
    assertTrue(excludedByDefault("reports/.DS_Store"))
    assertTrue(excludedByDefault("reports/Thumbs.db"))
  }

  /**
   * **A default names the directory, not what is under it**, and that is the reference's own
   * shape: the registered `node_modules` pattern carries no trailing globstar. ripgrep drops the subtree
   * because a matching *directory* is pruned from the walk, so the walker is what has to test a
   * directory against these - which is exactly what `DiskWorkspaceSearcher` does.
   */
  @Test
  fun aDefaultNamesTheDirectoryAndThePruningIsTheWalkers() {
    assertTrue(excludedByDefault("node_modules"))
    assertFalse(excludedByDefault("node_modules/left-pad/index.js"))
  }

  /** Nothing ordinary is excluded, which is the half that would fail silently. */
  @Test
  fun anOrdinaryFileIsNotExcluded() {
    assertFalse(excludedByDefault("notes.py"))
    assertFalse(excludedByDefault("reports/summary.ipynb"))
    assertFalse(excludedByDefault("reports"))
  }

  private companion object {
    /** `expandGlobalGlob` returns two patterns per one typed: the pattern, and the pattern's contents. */
    const val EXPANSIONS_PER_PATTERN = 2
  }
}
