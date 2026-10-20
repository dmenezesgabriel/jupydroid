package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles

/**
 * A workspace whose root holds the chats at [paths].
 *
 * What the panel lists where the Space has no server: with nothing configured, no chat, so the
 * panel claims none nobody made.
 *
 * ```
 * FakeChatFiles(listOf("untitled.chat")).list() // [ChatFile("untitled.chat")]
 * ```
 */
class FakeChatFiles(private val paths: List<String> = emptyList()) : ChatFiles {
  override suspend fun list(): List<ChatFile> = paths.map(::ChatFile)
}
