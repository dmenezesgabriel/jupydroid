package com.jupydroid.app.glob

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The regular expression each pattern compiles to, pinned by **equality to the reference's own
 * output** rather than by behaviour.
 *
 * Behaviour tests say a glob does the right thing on the cases somebody thought of; this says the
 * transcription of `parseRegExp` is the transcription, on every branch of it. Same gate
 * `EditorPaletteTest` uses for the palette, and for the same reason: parity is the stronger
 * assertion, and it is the one that fails when a branch is quietly wrong.
 *
 * **How the expected column was obtained**, so it can be re-derived rather than trusted:
 * `starsToRegExp`, `splitGlobAware` and `parseRegExp` were extracted from
 * `src/vs/base/common/glob.ts` at `f3fa55c39d3` into a standalone module, run under node over the
 * patterns below, and the reference's two-separator classes narrowed to the one this app can
 * produce - its `[/` backslash `]` to a plain slash, and the negated form likewise. That narrowing
 * is [PathGlob]'s one departure and the only edit made to the output.
 */
class PathGlobParityTest {
  @Test
  fun everyPatternCompilesToTheReferenceRegex() {
    REFERENCE_SOURCES.forEach { (pattern, expected) ->
      assertEquals("glob source for '$pattern'", expected, globRegexOf(pattern))
    }
  }

  private companion object {
    /** Pattern to the regular-expression source `parseRegExp` produces for it. */
    val REFERENCE_SOURCES = listOf(
      "*.py" to """[^/]*?\.py""",
      "**/*.py" to """(?:/|[^/]+/)*?[^/]*?\.py""",
      "**" to ".*",
      "**/**/*.py" to """(?:/|[^/]+/)*?[^/]*?\.py""",
      "?.py" to """[^/]\.py""",
      "notes.{py,txt}" to """notes\.(?:py|txt)""",
      "{**/*.py,notes.txt}" to """(?:(?:/|[^/]+/)*?[^/]*?\.py|notes\.txt)""",
      "[abc].py" to """[abc]\.py""",
      "[a-c].py" to """[a-c]\.py""",
      "[!a].py" to """[^a]\.py""",
      "[^a].py" to """[^a]\.py""",
      "[a/b].py" to """[ab]\.py""",
      "[]].py" to """[\]]\.py""",
      "notes{py" to "notes",
      "notes[py" to "notes",
      "some/**/*.js" to """some/(?:/|[^/]+/)*?[^/]*?\.js""",
      "**/reports/**" to "(?:/|[^/]+/)*?reports(?:/|[^/]+/|/[^/]+)*?",
      "reports/notes.py" to """reports/notes\.py""",
      "**/node_modules" to "(?:/|[^/]+/)*?node_modules",
      "**/bower_components" to "(?:/|[^/]+/)*?bower_components",
      "**/*.code-search" to """(?:/|[^/]+/)*?[^/]*?\.code-search""",
      "**/.git" to """(?:/|[^/]+/)*?\.git""",
      "**/.DS_Store" to """(?:/|[^/]+/)*?\.DS_Store""",
      "**/Thumbs.db" to """(?:/|[^/]+/)*?Thumbs\.db""",
      "a+b(c).py" to """a\+b\(c\)\.py""",
    )
  }
}
