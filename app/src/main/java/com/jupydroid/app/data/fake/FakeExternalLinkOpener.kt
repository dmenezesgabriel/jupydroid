package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.ExternalLinkOpener

/**
 * An [ExternalLinkOpener] that keeps what it was asked to open and leaves the app for none of it.
 *
 * `AppContainer`'s default, for [FakeDocumentSharer]'s reason: a preview or an instrumented test
 * must not put a browser in front of the screen it is asserting on.
 */
class FakeExternalLinkOpener : ExternalLinkOpener {
  private val asked = mutableListOf<String>()

  /** Every `open` so far, in order. */
  val opened: List<String> get() = asked.toList()

  override fun open(destination: String) {
    asked += destination
  }
}
