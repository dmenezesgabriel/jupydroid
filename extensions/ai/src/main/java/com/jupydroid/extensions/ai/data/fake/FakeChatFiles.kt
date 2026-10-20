package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles

/**
 * A workspace whose root holds the chats at [paths], and the ones [create] adds.
 *
 * What the panel lists where the Space has no server: with nothing configured, no chat, so the
 * panel claims none nobody made.
 *
 * ```
 * FakeChatFiles(listOf("untitled.chat")).list() // [ChatFile("untitled.chat")]
 * FakeChatFiles(listOf("untitled.chat")).create() // ChatFile("untitled1.chat")
 * ```
 */
class FakeChatFiles(paths: List<String> = emptyList()) : ChatFiles {
  private val held = paths.toMutableList()

  override suspend fun list(): List<ChatFile> = held.map(::ChatFile)

  /** Named as jupyter_server names an untitled file: `untitled`, then `untitled1`, `untitled2`... */
  override suspend fun create(): ChatFile {
    val free = generateSequence(0) { it + 1 }
      .map { n -> "untitled${if (n == 0) "" else n}${ChatFile.CHAT_EXTENSION}" }
      .first { it !in held }
    held += free
    return ChatFile(free)
  }
}
