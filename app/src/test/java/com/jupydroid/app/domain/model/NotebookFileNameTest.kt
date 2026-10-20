package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a file's path goes when it is renamed, copied or saved as, by the server's and JupyterLab's own rules. */
class NotebookFileNameTest {
  /**
   * Where a path ends up when something it lives under is renamed.
   *
   * `Context._onFileChanged` (`packages/docregistry/src/context.ts`), which matches by prefix and
   * says why in its own comment: "When folder name changed, `oldPath` is `foo`, `newPath` is `bar`
   * and `this._path` is `foo/test`, we should update `foo/test` to `bar/test` as well". A document
   * open inside a renamed folder has moved just as surely as one renamed directly.
   */
  @Test
  fun aRenamedPathIsTheOneThatMoved() {
    assertEquals("final.ipynb", pathAfterRename("draft.ipynb", from = "draft.ipynb", to = "final.ipynb"))
  }

  @Test
  fun aPathBeneathARenamedFolderMovesWithIt() {
    assertEquals("bar/test", pathAfterRename("foo/test", from = "foo", to = "bar"))
    assertEquals("bar/deep/test", pathAfterRename("foo/deep/test", from = "foo", to = "bar"))
  }

  /**
   * A prefix that is not a whole segment has not moved - `foobar/x` is not inside `foo`.
   *
   * The reference's `indexOf(oldPath) === 0` says otherwise, and would rewrite an unrelated
   * document's path to `barbar/x`. Comparing whole segments is the correction, not a departure:
   * every other rule here is about a path being made of segments.
   */
  @Test
  fun aPathThatMerelyStartsTheSameHasNotMoved() {
    assertNull(pathAfterRename("foobar/x", from = "foo", to = "bar"))
    assertNull(pathAfterRename("other.ipynb", from = "draft.ipynb", to = "final.ipynb"))
  }

  /** A Save As takes a path, so a copy can go into another folder: every segment is a name. */
  @Test
  fun aPathIsValidWhenEverySegmentIsAName() {
    assertTrue(isValidWorkspacePath("notes-copy.py"))
    assertTrue(isValidWorkspacePath("reports/2026/notes.py"))
  }

  /** An empty segment, a climb out of the root or a hidden name is refused, as each would be as a name. */
  @Test
  fun aPathWithASegmentThatIsNotANameIsRefused() {
    assertFalse(isValidWorkspacePath("reports//notes.py"))
    assertFalse(isValidWorkspacePath("../notes.py"))
    assertFalse(isValidWorkspacePath("reports/.hidden"))
    assertFalse(isValidWorkspacePath("/notes.py"))
  }

  /**
   * A copy is named from its source with any earlier `-Copy<n>` taken out first, as
   * jupyter_server's `copy` runs `copy_pat.sub(".", from_name)` before it counts
   * (`services/contents/manager.py`, `copy_pat = re.compile(r"\-Copy\d*\.")`).
   */
  @Test
  fun aCopysSuffixIsTakenOutBeforeTheNextOneIsCounted() {
    assertEquals("other.py", nameBeforeCopies("other-Copy1.py"))
    assertEquals("other.py", nameBeforeCopies("other-Copy.py"))
    assertEquals("other.py", nameBeforeCopies("other.py"))
  }

  /** The pattern needs the dot after it, and `re.sub` replaces every match, not the first. */
  @Test
  fun onlyACopySuffixBeforeADotIsTakenOutAndEveryOneIs() {
    assertEquals("Makefile-Copy1", nameBeforeCopies("Makefile-Copy1"))
    assertEquals("a.b.ipynb", nameBeforeCopies("a-Copy1.b-Copy2.ipynb"))
  }
}
