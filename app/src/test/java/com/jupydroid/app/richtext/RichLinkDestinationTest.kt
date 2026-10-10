package com.jupydroid.app.richtext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichLinkDestinationTest {
  @Test
  fun aWebAddressLeavesTheApp() {
    assertTrue("https://jupyter.org".isWebLinkDestination())
    assertTrue("http://www.jupyter.org/a?b=c#d".isWebLinkDestination())
  }

  @Test
  fun aMailAddressLeavesTheApp() {
    assertTrue("mailto:a@b.org".isWebLinkDestination())
  }

  /** A scheme is case-insensitive (RFC 3986 section 3.1), and an author types what they type. */
  @Test
  fun aSchemeIsReadWhateverItsCase() {
    assertTrue("HTTPS://jupyter.org".isWebLinkDestination())
  }

  /** Each of these names another app's entry point, or script, chosen by whoever wrote the notebook. */
  @Test
  fun noOtherSchemeLeavesTheApp() {
    listOf("javascript:alert(1)", "intent://scan/#Intent;end", "file:///sdcard/a", "content://a/b", "tel:112")
      .forEach { assertFalse("$it must not leave the app", it.isWebLinkDestination()) }
  }

  @Test
  fun aPathIsNotAWebAddress() {
    listOf("notes.md", "../data/a.csv", "/etc/hosts", "#results", "", "httpserver.py")
      .forEach { assertFalse("'$it' is not a web address", it.isWebLinkDestination()) }
  }
}
