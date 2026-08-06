package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `|` marks the cursor. */
class CompletionQueryTest {
  @Test
  fun asksOnceThereIsEnoughOfAnIdentifierToNarrowAnything() {
    assertEquals(CompletionQuery("pri", 3), queryIn("pri|"))
    assertEquals(CompletionQuery("x = np.ar", 9), queryIn("x = np.ar|"))
  }

  /** One letter matches most of a kernel's namespace, so it is a round trip that buys nothing. */
  @Test
  fun staysQuietUntilTheSecondCharacter() {
    assertNull(queryIn("p|"))
  }

  @Test
  fun staysQuietWhereNoIdentifierIsBeingTyped() {
    assertNull(queryIn("print(|"))
    assertNull(queryIn("x = |"))
    assertNull(queryIn("|"))
  }

  /** Completing from the middle of a word would replace the half the user meant to keep. */
  @Test
  fun staysQuietInsideAWordRatherThanAtItsEnd() {
    assertNull(queryIn("pri|nt"))
  }

  @Test
  fun countsDigitsAndUnderscoresAsPartOfTheIdentifier() {
    assertEquals(CompletionQuery("my_var", 6), queryIn("my_var|"))
    assertEquals(CompletionQuery("x2", 2), queryIn("x2|"))
  }

  /** A dot ends the identifier, so `np.ar` asks about `ar` rather than about nothing. */
  @Test
  fun treatsAnAttributeAsItsOwnIdentifier() {
    assertEquals(CompletionQuery("np.ar", 5), queryIn("np.ar|"))
    assertNull(queryIn("np.|"))
  }

  private fun queryIn(marked: String): CompletionQuery? {
    val cursor = marked.indexOf('|')
    require(cursor >= 0) { "no cursor in \"$marked\"" }
    return completionQueryAt(marked.replace("|", ""), cursor)
  }
}
