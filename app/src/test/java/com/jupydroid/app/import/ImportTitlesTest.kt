package com.jupydroid.app.import

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportTitlesTest {
  @Test
  fun ipynbFilename_stripsExtension() {
    assertEquals("Intro to NumPy", importTitleFrom("Intro to NumPy.ipynb"))
  }

  @Test
  fun ipynbFilename_ignoresExtensionCase() {
    assertEquals("notes", importTitleFrom("notes.IPYNB"))
  }

  @Test
  fun dotInsideNameIsKept() {
    assertEquals("My.Notes", importTitleFrom("My.Notes.ipynb"))
  }

  @Test
  fun nonIpynbFilename_isLeftAsIs() {
    assertEquals("script.py", importTitleFrom("script.py"))
  }

  @Test
  fun nullFilename_defaultsToImportedNotebook() {
    assertEquals("Imported notebook", importTitleFrom(null))
  }

  @Test
  fun blankFilename_defaultsToImportedNotebook() {
    assertEquals("Imported notebook", importTitleFrom("   "))
  }

  @Test
  fun extensionOnly_defaultsToImportedNotebook() {
    assertEquals("Imported notebook", importTitleFrom(".ipynb"))
  }
}
