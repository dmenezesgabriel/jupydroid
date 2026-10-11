package com.jupydroid.app.domain.port

/**
 * Reads the image a markdown cell names by its web address, as a browser reads an `<img>`'s `src`.
 *
 * A port because the fetch is a network mechanism with a cache behind it, and because a rendered
 * image must be testable without the network.
 *
 * ```
 * val bytes = reader.imageBytes("https://jupyter.org/assets/logo.png")
 * ```
 */
interface RemoteImageReader {
  /**
   * The bytes served at [url], undecoded, or null when it serves no image this app takes: an
   * error status, a host that cannot be reached, or a body over [MAX_IMAGE_BYTES].
   */
  suspend fun imageBytes(url: String): ByteArray?

  companion object {
    /** GitHub's image proxy's own cap, camo's `CAMO_LENGTH_LIMIT` (ADR-0087). */
    const val MAX_IMAGE_BYTES: Int = 5 * 1024 * 1024
  }
}
