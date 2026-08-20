package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The snackbar's wording, which is the whole of what a user is told about an export - and, for a
 * failure, the only place the server's own sentence reaches them.
 */
class ExportReportMessageTest {
  @Test
  fun namesTheFileThatWasWritten() {
    val written = ExportReport.Written("reports/Analysis.html", "text/html")

    assertEquals("Exported to reports/Analysis.html", exportReportMessage(written))
  }

  /** The path and not just the name, because an export lands beside the notebook and may be deep. */
  @Test
  fun namesTheWholePathSoAnOverwriteIsNeverAmbiguous() {
    assertEquals("Exported to a/b/c.md", exportReportMessage(ExportReport.Written("a/b/c.md", "text/markdown")))
  }

  /** Not wrapped, not prefixed: *"Pandoc wasn't found."* is actionable and "Export failed" is not. */
  @Test
  fun repeatsTheServersOwnSentenceForARefusal() {
    val refused = ExportReport.Refused("cannot export to 'rst': nbconvert failed: Pandoc wasn't found.")

    assertEquals("cannot export to 'rst': nbconvert failed: Pandoc wasn't found.", exportReportMessage(refused))
  }
}

/**
 * The progress bar's name, which is the only text an indeterminate bar has - what a screen reader
 * says and what a flow waits for.
 */
class ExportingLabelTest {
  @Test
  fun namesTheFormatBeingConverted() {
    assertEquals("Exporting to html", exportingLabel("html"))
  }

  @Test
  fun namesWhicheverFormatItIs() {
    assertEquals("Exporting to slides", exportingLabel("slides"))
  }
}
