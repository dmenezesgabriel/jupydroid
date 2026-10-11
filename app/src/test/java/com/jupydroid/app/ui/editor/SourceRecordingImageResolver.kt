package com.jupydroid.app.ui.editor

/** The resolver another sits inside: answers nothing, and records what it was asked. */
internal class SourceRecordingImageResolver : RichImageResolver {
  val asked = mutableListOf<String>()

  override suspend fun imageBytes(source: String): ByteArray? {
    asked += source
    return null
  }
}
