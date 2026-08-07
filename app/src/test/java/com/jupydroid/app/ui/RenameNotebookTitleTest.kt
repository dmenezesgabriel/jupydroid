package com.jupydroid.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the pre-selection stops when the rename dialog opens.
 *
 * JupyterLab's rename dialog fills the field with the whole name and selects only the stem -
 * `setSelectionRange(0, value.length - ext.length)` with `PathExt.extname`
 * (`packages/docmanager/src/dialogs.ts:262-290`) - so typing replaces the name and keeps the
 * extension. It matters here because a new Python file is titled `untitled.py`.
 *
 * JupyterLab actually has three rules that disagree - `PathExt.extname` in the dialog,
 * `lastIndexOf('.')` in the file browser (`listing.ts:3948-3952`) and `indexOf('.')` in the
 * single-document title bar (`shell.ts:2850-2855`). This is the dialog's, because a dialog is
 * what this is, and it is stated once.
 */
class RenameNotebookTitleTest {
  @Test
  fun anExtensionIsLeftOutOfTheSelection() {
    assertEquals(8, titleStemEnd("untitled.py"))
  }

  @Test
  fun aTitleWithNoExtensionIsSelectedWhole() {
    assertEquals(8, titleStemEnd("Untitled"))
    assertEquals(11, titleStemEnd("Intro to Nu"))
  }

  /** A leading dot is not an extension - `PathExt.extname('.bashrc')` is empty. */
  @Test
  fun aLeadingDotIsPartOfTheName() {
    assertEquals(7, titleStemEnd(".bashrc"))
  }

  /** Only the last dot counts: `extname('index.coffee.md')` is `.md`. */
  @Test
  fun onlyTheLastDotEndsTheStem() {
    assertEquals(3, titleStemEnd("a.b.c"))
  }

  /** A trailing dot is an extension of its own, `extname('index.')` being `.`. */
  @Test
  fun aTrailingDotEndsTheStem() {
    assertEquals(5, titleStemEnd("notes."))
  }

  @Test
  fun anEmptyTitleSelectsNothing() {
    assertEquals(0, titleStemEnd(""))
  }
}
