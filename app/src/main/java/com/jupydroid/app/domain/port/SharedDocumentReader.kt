package com.jupydroid.app.domain.port

/**
 * Reads a document another app handed over - a `.ipynb` opened from a file manager or a mail
 * attachment - which arrives as a URI that app serves, never as a path in the workspace.
 *
 * A port because the read is a platform mechanism, and because what lands in the workspace must be
 * testable without a provider to serve it.
 *
 * ```
 * val bytes = reader.readBytes("content://media/external/file/42")
 * ```
 */
interface SharedDocumentReader {
  /**
   * Every byte behind [uri], decoded as nothing: the document is copied in as it is, the way
   * JupyterLab's upload and Termux's file receiver store what they are given.
   */
  suspend fun readBytes(uri: String): ByteArray
}
