package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTranscriptTest {
  private val question = ChatMessage(id = "q", body = "hello?", fromMe = true)

  @Test
  fun aNewMessageGoesBeneathTheOthers() {
    val reply = ChatMessage(id = "r", body = "hi", fromMe = false)

    assertEquals(listOf(question, reply), ChatTranscript(listOf(question)).with(reply).messages)
  }

  /** A persona streams its answer as updates to one message (jupyter_ai_acp_client 0.3.3 `base_acp_persona.py`). */
  @Test
  fun anUpdateReplacesTheMessageWithItsIdInPlace() {
    val partial = ChatMessage(id = "r", body = "Open", fromMe = false)
    val whole = partial.copy(body = "OpenCode isn't configured yet.")

    val transcript = ChatTranscript(listOf(question, partial)).with(whole)

    assertEquals(listOf(question, whole), transcript.messages)
  }
}
