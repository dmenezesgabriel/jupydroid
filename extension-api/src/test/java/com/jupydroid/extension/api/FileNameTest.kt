package com.jupydroid.extension.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a notebook may be called, once its name *is* its file.
 *
 * The rule is JupyterLab's, read out of
 * `jupyterlab@7f0e6906 packages/docmanager/src/dialogs.ts`:
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
class FileNameTest {
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
}
