package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a notebook may be called, once its name *is* its file.
 *
 * The rule is JupyterLab's, read out of
 * `reference/research_repos/jupyterlab/packages/docmanager/src/dialogs.ts`:
 *
 * ```ts
 * // Disallows "/", "\", and ":" in file names, as well as names with zero length.
 * const validNameExp = /[/\\:]/;
 * return name.length > 0 && !validNameExp.test(name);
 * ```
 *
 * **It rejects rather than sanitises**, and that is the part worth copying. Quietly rewriting
 * `a/b` into `a-b` would give the user a notebook they did not name and cannot find by the name
 * they typed; the reference shows an error saying exactly which characters are refused, and so
 * do we.
 */
class NotebookFileNameTest {
  @Test
  fun anOrdinaryNameIsFine() {
    assertTrue(isValidFileName("Analysis of sales"))
  }

  @Test
  fun aNameMustHaveSomethingInIt() {
    assertFalse(isValidFileName(""))
    assertFalse(isValidFileName("   "))
  }

  /** The three the reference names, and no more: this is a rejection list, not a taste list. */
  @Test
  fun separatorsAndColonsAreRefused() {
    assertFalse(isValidFileName("a/b"))
    assertFalse(isValidFileName("a\\b"))
    assertFalse(isValidFileName("a:b"))
  }

  @Test
  fun everythingElseIsAllowed() {
    assertTrue(isValidFileName("π ≈ 3.14, or is it?"))
    assertTrue(isValidFileName("notebook (2)"))
    assertTrue(isValidFileName("a.b"))
  }

  /**
   * A leading dot would hide the file from the explorer that Phase X exists to build, and from
   * `ls`. jupyter_server treats dotfiles as hidden and will not serve them by default
   * (`ContentsManager.allow_hidden` is false), so this is the server's rule as much as ours.
   */
  @Test
  fun aHiddenNameIsRefused() {
    assertFalse(isValidFileName(".secret"))
  }

  /**
   * A name carries its own suffix now, so `Report.ipynb` is a name and not a name plus a rule.
   *
   * The two helpers that put the suffix on and took it back off are gone with it: the id is the
   * path (`Contents.IModel.path`), and every boundary that had to add or strip `.ipynb` was a
   * chance to do it twice - which is exactly what `Nested.ipynb.ipynb` was.
   */
  @Test
  fun aNameWithItsSuffixIsAName() {
    assertTrue(isValidFileName("Analysis of sales.ipynb"))
    assertTrue(isValidFileName("script.py"))
  }

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
}
