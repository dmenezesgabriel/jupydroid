package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.ErrorLogSharer

/**
 * An [ErrorLogSharer] that counts what it was asked to share and offers it to nobody, so a test or a
 * device with no external storage never throws a chooser at the screen.
 *
 * ```
 * FakeErrorLogSharer().also { it.share() }.shareCount
 * ```
 */
class FakeErrorLogSharer : ErrorLogSharer {
  /** How many times `share` was called. */
  var shareCount: Int = 0
    private set

  override suspend fun share() {
    shareCount += 1
  }
}
