package com.jupydroid.app.ui.editor

import androidx.compose.ui.platform.UriHandler

/** A [UriHandler] that keeps what a tapped link asked it to open, and opens none of it. */
internal class RecordingUriHandler : UriHandler {
  private val asked = mutableListOf<String>()

  /** Every `openUri` so far, in order. */
  val opened: List<String> get() = asked.toList()

  override fun openUri(uri: String) {
    asked += uri
  }
}
