package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every header and body here was captured from the tablet unless a case says otherwise.
 */
class NbConvertResponseTest {
  @Test
  fun fileName_readsTheRfc5987FormTheServerActuallySends() {
    val header = "attachment; filename*=utf-8''p2j-2-export-formats.html"

    assertEquals("p2j-2-export-formats.html", NbConvertResponse.fileName(header))
  }

  /** `slides` is `<stem>.slides.html`, so the extension is not the last dot's worth. */
  @Test
  fun fileName_keepsATwoPartExtension() {
    val header = "attachment; filename*=utf-8''p2j-2-export-formats.slides.html"

    assertEquals("p2j-2-export-formats.slides.html", NbConvertResponse.fileName(header))
  }

  /** A notebook's title is its file name, and titles have spaces in them. */
  @Test
  fun fileName_decodesAPercentEncodedName() {
    val header = "attachment; filename*=utf-8''Quarterly%20Report.html"

    assertEquals("Quarterly Report.html", NbConvertResponse.fileName(header))
  }

  /** Not what this rootfs sends, but what tornado sends for a plain ASCII name elsewhere. */
  @Test
  fun fileName_readsThePlainFormToo() {
    assertEquals("Analysis.md", NbConvertResponse.fileName("""attachment; filename="Analysis.md""""))
  }

  @Test
  fun fileName_prefersTheExtendedFormWhenBothAreSent() {
    val header = """attachment; filename="Analysis.html"; filename*=utf-8''Quarterly%20Report.html"""

    assertEquals("Quarterly Report.html", NbConvertResponse.fileName(header))
  }

  @Test
  fun fileName_isNothingWhenTheHeaderIsAbsentOrNamesNoFile() {
    assertNull(NbConvertResponse.fileName(null))
    assertNull(NbConvertResponse.fileName("attachment"))
  }

  @Test
  fun mediaType_dropsTheCharsetBecauseItBelongsToTheTransfer() {
    assertEquals("text/html", NbConvertResponse.mediaType("text/html; charset=utf-8"))
  }

  @Test
  fun mediaType_leavesOneWithNoParametersAlone() {
    assertEquals("application/zip", NbConvertResponse.mediaType("application/zip"))
  }

  @Test
  fun mediaType_isNothingWhenTheServerSentNone() {
    assertNull(NbConvertResponse.mediaType(null))
    assertNull(NbConvertResponse.mediaType("  "))
  }

  @Test
  fun failureMessage_isNbconvertsOwnSentenceOutOfTornadosPage() {
    val page = "<html><body><h1>500 : Internal Server Error</h1>" +
      "<pre>nbconvert failed: Pandoc wasn&#39;t found.\nPlease check that pandoc is installed.</pre></body></html>"

    assertEquals(
      "nbconvert failed: Pandoc wasn't found.\nPlease check that pandoc is installed.",
      NbConvertResponse.failureMessage(page),
    )
  }

  @Test
  fun failureMessage_findsTheLineEvenWithNoPreBlockAroundIt() {
    val page = "<html><body>nbconvert failed: Playwright is not installed.</body></html>"

    assertEquals("nbconvert failed: Playwright is not installed.", NbConvertResponse.failureMessage(page))
  }

  @Test
  fun failureMessage_saysSomethingRatherThanNothingForAnEmptyBody() {
    assertEquals("the server said nothing", NbConvertResponse.failureMessage(""))
  }

  /** A snackbar is not a log; a 3 kB error page pasted into one is unreadable. */
  @Test
  fun failureMessage_isBoundedForAServerThatFailedInSomeOtherWay() {
    val message = NbConvertResponse.failureMessage("x".repeat(4000))

    assertTrue("expected a bounded message, got ${message.length} characters", message.length <= 500)
  }
}
