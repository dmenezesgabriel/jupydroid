package com.jupydroid.app.domain.port

/**
 * Offers the error log, whole, to whatever else on the device the person picks.
 *
 * Its own port rather than an operation on [ErrorLog]: writing a log and handing it to another app are
 * two roles, and the code that records a crash should not depend on a way of sharing. An entry is redacted
 * when it is written, so what is offered never carries a secret.
 *
 * ```
 * errorLogSharer.share()
 * ```
 */
interface ErrorLogSharer {
  /** Offers every entry the log holds. Throws `IOException` if the copy to hand out cannot be made. */
  suspend fun share()
}
