package com.jupydroid.app.domain.port

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The message is what a user sees when a conversion fails, so it is a thing under test rather
 * than a string literal - a `NotebookExporter` has nothing else to report with.
 */
class NotebookExportExceptionTest {
  @Test
  fun namesTheFormatThatCouldNotBeConvertedTo() {
    val refused = NotebookExportException("rst", "nbconvert failed: Pandoc wasn't found.")

    assertEquals("cannot export to 'rst': nbconvert failed: Pandoc wasn't found.", refused.message)
  }

  /** A failure before a format was picked - listing them - has none to name. */
  @Test
  fun saysOnlyWhatWentWrongWhenNoFormatHadBeenChosen() {
    assertEquals("the Space is not running", NotebookExportException(null, "the Space is not running").message)
  }
}
