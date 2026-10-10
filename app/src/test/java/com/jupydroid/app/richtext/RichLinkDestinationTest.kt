package com.jupydroid.app.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

  @Test
  fun aRelativeLinkResolvesAgainstTheNotebooksDirectory() {
    assertEquals("a/notes.md", "notes.md".workspaceLinkPath("a"))
    assertEquals("a/notes.md", "./notes.md".workspaceLinkPath("a"))
    assertEquals("a/sub/notes.md", "sub/notes.md".workspaceLinkPath("a"))
  }

  @Test
  fun aNotebookAtTheRootResolvesFromTheRoot() {
    assertEquals("notes.md", "notes.md".workspaceLinkPath(""))
  }

  @Test
  fun aParentSegmentClimbsOneDirectory() {
    assertEquals("notes.md", "../notes.md".workspaceLinkPath("a"))
    assertEquals("a/c.md", "b/../c.md".workspaceLinkPath("a"))
  }

  /** Nothing above the workspace root is addressable (`WorkspaceRepository`), so no link reaches it. */
  @Test
  fun aLinkAboveTheRootIsNotAWorkspaceLink() {
    assertNull("../../x.md".workspaceLinkPath("a"))
    assertNull("../x.md".workspaceLinkPath(""))
  }

  /** `decodeURIComponent` before the contents API (jupyterlab@7f0e690 `rendermime/src/registry.ts`). */
  @Test
  fun anEscapedCharacterIsDecoded() {
    assertEquals("a/my notes+1.md", "my%20notes+1.md".workspaceLinkPath("a"))
  }

  @Test
  fun aMalformedEscapeIsNotAWorkspaceLink() {
    assertNull("100%zz.md".workspaceLinkPath("a"))
  }

  /** The file is the destination; a fragment or a query says where inside it, which T3 decides. */
  @Test
  fun aFragmentOrQueryIsLeftOffThePath() {
    assertEquals("a/notes.md", "notes.md#intro".workspaceLinkPath("a"))
    assertEquals("a/notes.md", "notes.md?x=1".workspaceLinkPath("a"))
  }

  @Test
  fun aTrailingSlashNamesTheDirectory() {
    assertEquals("a/docs", "docs/".workspaceLinkPath("a"))
  }

  /** `URLExt.isLocal` (jupyterlab@7f0e690 `coreutils/src/url.ts`): no scheme and no leading slash. */
  @Test
  fun onlyARelativePathIsAWorkspaceLink() {
    listOf("https://jupyter.org", "mailto:a@b.org", "javascript:alert(1)", "/etc/hosts", "//host/x", "#results", "")
      .forEach { assertNull("'$it' is not a workspace link", it.workspaceLinkPath("a")) }
  }
}
