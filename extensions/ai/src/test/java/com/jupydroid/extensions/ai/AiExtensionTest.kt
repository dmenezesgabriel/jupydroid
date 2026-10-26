package com.jupydroid.extensions.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the extension declares for the host to place, read without drawing any of it. */
class AiExtensionTest {
  @Test
  fun aChatFileOpensAsTheExtensionsDocument() {
    val documents = AiExtension().documents

    assertEquals(listOf("ai.chat" to ".chat"), documents.map { it.id to it.suffix })
  }

  /** jupyter-chat's `moveChat` from the main area lands in its own chat panel, never another's. */
  @Test
  fun aChatTabMovesToTheChatPanel() {
    val extension = AiExtension()

    assertEquals(extension.sidePanels.single().id, extension.documents.single().movesTo?.panelId)
  }
}
