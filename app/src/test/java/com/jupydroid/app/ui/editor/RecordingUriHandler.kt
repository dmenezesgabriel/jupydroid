package com.jupydroid.app.ui.editor

import androidx.compose.ui.platform.UriHandler

/** The handler around the one under test: it records what was passed on to it, and opens nothing. */
internal class RecordingUriHandler : UriHandler {
  val opened = mutableListOf<String>()

  override fun openUri(uri: String) {
    opened += uri
  }
}
