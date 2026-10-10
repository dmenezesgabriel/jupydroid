package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingLinkUriHandlerTest {
  private val outer = RecordingUriHandler()
  private val followed = mutableListOf<String>()
  private val links = HeadingLinkUriHandler(outer) { followed += it }

  @Test
  fun aBareFragmentIsFollowedInThisDocument() {
    links.openUri("#My%20Results")

    assertEquals(listOf("My Results"), followed)
    assertEquals(emptyList<String>(), outer.opened)
  }

  @Test
  fun anythingElseGoesToTheHandlerAroundIt() {
    listOf("notes.md#intro", "https://jupyter.org/#a", "#").forEach(links::openUri)

    assertEquals(listOf("notes.md#intro", "https://jupyter.org/#a", "#"), outer.opened)
    assertEquals(emptyList<String>(), followed)
  }

  @Test
  fun aMissNamesTheFragment() {
    assertEquals("No heading #Conclusion", missingHeadingMessage("Conclusion"))
  }
}
