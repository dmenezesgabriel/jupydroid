package com.jupydroid.app.domain.port

/**
 * Hands a link's destination to whatever else on the device opens it - the browser for a web
 * address, a mail app for a `mailto:`.
 *
 * A port because the hand-off is a platform mechanism, and because a rendered link's tap must be
 * observable in a test without a browser coming to the front of the device that runs it.
 *
 * ```
 * opener.open("https://jupyter.org")
 * ```
 */
interface ExternalLinkOpener {
  /**
   * Opens [destination], which the caller has already judged safe to leave the app with - see
   * `isWebLinkDestination`. A device with nothing that takes it opens nothing.
   */
  fun open(destination: String)
}
