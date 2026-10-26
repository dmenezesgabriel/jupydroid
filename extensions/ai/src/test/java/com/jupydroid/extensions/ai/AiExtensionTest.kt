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
}
