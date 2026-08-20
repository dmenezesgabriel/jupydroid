package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.DocumentSharer

/**
 * A [DocumentSharer] that keeps what it was asked to share and offers it to nobody.
 *
 * `AppContainer`'s default, like every other fake, and here it earns that twice over: a Compose
 * preview or an instrumented test must not throw an `Intent` at the device in the middle of a run.
 */
class FakeDocumentSharer : DocumentSharer {
  private val offered = mutableListOf<Pair<String, String>>()

  /** Every `share` so far, as `path to mediaType`, in order. */
  val shared: List<Pair<String, String>> get() = offered.toList()

  override fun share(path: String, mediaType: String) {
    offered += path to mediaType
  }
}
