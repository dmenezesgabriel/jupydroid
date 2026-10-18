package com.jupydroid.extensions.ai.data.logcat

import org.junit.Assert.assertEquals
import org.junit.Test

/** What `adb logcat -s JupydroidChat` prints: one JSON object, read with `jq`. */
class ChatLogLineTest {
  @Test
  fun fieldsAreRenderedBesideTheStageInTheOrderGiven() {
    val line = chatLogLine("chat.socket.failed", linkedMapOf("error" to "EOFException", "chat" to "untitled.chat"))

    assertEquals("""{"stage":"chat.socket.failed","error":"EOFException","chat":"untitled.chat"}""", line)
  }

  /** A close frame's reason is whatever the server said. */
  @Test
  fun quotesAndNewlinesInAValueAreEscaped() {
    val line = chatLogLine("chat.socket.closed", mapOf("reason" to "\"outside\"\nroot"))

    assertEquals("""{"stage":"chat.socket.closed","reason":"\"outside\"\nroot"}""", line)
  }
}
