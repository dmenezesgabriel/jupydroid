package com.jupydroid.app.data.fake

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeDocumentSharerTest {
  @Test
  fun keepsWhatItWasAskedToShare() {
    val sharer = FakeDocumentSharer()

    sharer.share("reports/Analysis.html", "text/html")
    sharer.share("Analysis.zip", "application/zip")

    assertEquals(
      listOf("reports/Analysis.html" to "text/html", "Analysis.zip" to "application/zip"),
      sharer.shared,
    )
  }

  @Test
  fun hasSharedNothingUntilItIsAsked() {
    assertEquals(emptyList<Pair<String, String>>(), FakeDocumentSharer().shared)
  }
}
