package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    assertTrue(isValidNotebookName("Analysis of sales"))
  }

  @Test
  fun aNameMustHaveSomethingInIt() {
    assertFalse(isValidNotebookName(""))
    assertFalse(isValidNotebookName("   "))
  }

  /** The three the reference names, and no more: this is a rejection list, not a taste list. */
  @Test
  fun separatorsAndColonsAreRefused() {
    assertFalse(isValidNotebookName("a/b"))
    assertFalse(isValidNotebookName("a\\b"))
    assertFalse(isValidNotebookName("a:b"))
  }

  @Test
  fun everythingElseIsAllowed() {
    assertTrue(isValidNotebookName("π ≈ 3.14, or is it?"))
    assertTrue(isValidNotebookName("notebook (2)"))
    assertTrue(isValidNotebookName("a.b"))
  }

  /**
   * A leading dot would hide the file from the explorer that Phase X exists to build, and from
   * `ls`. jupyter_server treats dotfiles as hidden and will not serve them by default
   * (`ContentsManager.allow_hidden` is false), so this is the server's rule as much as ours.
   */
  @Test
  fun aHiddenNameIsRefused() {
    assertFalse(isValidNotebookName(".secret"))
  }

  @Test
  fun theFileIsTheNamePlusTheSuffix() {
    assertEquals("Analysis of sales.ipynb", notebookFileName("Analysis of sales"))
  }

  /** Surrounding space is trimmed, because a file called `"a "` is one nobody can type twice. */
  @Test
  fun surroundingSpaceIsTrimmed() {
    assertEquals("Report.ipynb", notebookFileName("  Report  "))
  }

  @Test
  fun theNameComesBackOffTheFile() {
    assertEquals("Analysis of sales", notebookNameOf("Analysis of sales.ipynb"))
  }
}
