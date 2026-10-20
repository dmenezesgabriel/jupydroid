package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesContract

class FakeChatFilesTest : ChatFilesContract() {
  override fun chatFiles(paths: List<String>): ChatFiles = FakeChatFiles(paths)
}
