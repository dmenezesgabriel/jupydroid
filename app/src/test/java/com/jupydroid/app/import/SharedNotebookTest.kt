package com.jupydroid.app.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedNotebookTest {
  @Test
  fun aNotebookKeepsItsOwnNameWithItsSuffix() {
    assertEquals(
      SharedNotebook("content://media/1", "Intro to NumPy.ipynb"),
      sharedNotebookFrom("content://media/1", "Intro to NumPy.ipynb"),
    )
  }

  @Test
  fun theSuffixIsMatchedWhateverItsCase() {
    assertEquals("notes.IPYNB", sharedNotebookFrom("content://media/1", "notes.IPYNB")?.fileName)
  }

  @Test
  fun aFileThatIsNotANotebookIsNotShared() {
    assertNull(sharedNotebookFrom("content://media/1", "script.py"))
  }

  @Test
  fun aDocumentWithNoNameIsNotShared() {
    assertNull(sharedNotebookFrom("content://media/1", null))
  }

  @Test
  fun aSuffixWithNoStemLandsUnderADefaultName() {
    assertEquals("Imported notebook.ipynb", sharedNotebookFrom("content://media/1", " .ipynb ")?.fileName)
  }

  /** The name is the providing app's to report, so a folder in it is not one the workspace has. */
  @Test
  fun aNameThatCarriesFoldersKeepsOnlyItsLastSegment() {
    assertEquals("x.ipynb", sharedNotebookFrom("content://media/1", "../../x.ipynb")?.fileName)
  }
}
