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
   *
   * @throws SharedDocumentTooLarge when the document is over [MAX_BYTES]; it is never read whole.
   */
  suspend fun readBytes(uri: String): ByteArray

  companion object {
    /**
     * `jupyterlab@7f0e690 packages/filebrowser/src/model.ts` `LARGE_FILE_SIZE`: the size past which
     * JupyterLab will not take a file without being asked. An import has no one to ask.
     */
    const val MAX_BYTES: Int = 15 * 1024 * 1024
  }
}

/** A shared document held more than [limitBytes], so it was refused rather than read to its end. */
class SharedDocumentTooLarge(val limitBytes: Int) :
  IllegalArgumentException("the shared document is over $limitBytes bytes; expected at most $limitBytes")
