package com.jupydroid.app

import com.jupydroid.app.data.fake.FakeExternalLinkOpener
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderedLinkUriHandlerTest {
  private val opener = FakeExternalLinkOpener()
  private val links = RenderedLinkUriHandler(opener)

  @Test
  fun aWebAddressGoesToTheOpener() {
    links.openUri("https://jupyter.org")

    assertEquals(listOf("https://jupyter.org"), opener.opened)
  }

  @Test
  fun anythingElseOpensNothing() {
    links.openUri("javascript:alert(1)")
    links.openUri("notes.md")

    assertEquals(emptyList<String>(), opener.opened)
  }
}
