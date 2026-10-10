package com.jupydroid.app.data.fake

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeExternalLinkOpenerTest {
  @Test
  fun keepsWhatItWasAskedToOpen() {
    val opener = FakeExternalLinkOpener()

    opener.open("https://jupyter.org")
    opener.open("mailto:a@b.org")

    assertEquals(listOf("https://jupyter.org", "mailto:a@b.org"), opener.opened)
  }

  @Test
  fun hasOpenedNothingUntilItIsAsked() {
    assertEquals(emptyList<String>(), FakeExternalLinkOpener().opened)
  }
}
