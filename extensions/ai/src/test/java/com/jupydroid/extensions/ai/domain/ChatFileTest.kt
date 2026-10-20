package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatFileTest {
  /** jupyter-chat lists a chat without its extension (`getChatNames`). */
  @Test
  fun aChatIsNamedWithoutItsExtension() {
    assertEquals("untitled", ChatFile("untitled.chat").name)
  }

  /** jupyter-chat's list is sorted by `localeCompare`, which puts `b` after `A` (`placeholder.tsx`). */
  @Test
  fun chatsAreListedByNameWhateverTheirCase() {
    val listed = listOf(ChatFile("b.chat"), ChatFile("C.chat"), ChatFile("A.chat")).byName()

    assertEquals(listOf("A", "b", "C"), listed.map(ChatFile::name))
  }

  /** jupyter-chat's `resolveChatRenamePath` puts `.chat` on a name typed without it, and not twice. */
  @Test
  fun aChatNamedIsTheFileOfThatNameAtTheRoot() {
    assertEquals(ChatFile("notes.chat"), ChatFile.named("notes"))
    assertEquals(ChatFile("notes.chat"), ChatFile.named("notes.chat"))
  }
}
