package com.jupydroid.app.data.intent

import android.content.ContentResolver
import android.net.Uri
import com.jupydroid.app.domain.port.SharedDocumentReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a shared document through the [ContentResolver], which is how any app reaches what another
 * one serves - `termux-app@3df69d1 app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java`
 * copies a shared file in through the same `openInputStream`.
 *
 * Off the main thread: a notebook carrying image outputs can run to megabytes.
 *
 * ```
 * ContentResolverSharedDocumentReader(context.contentResolver).readBytes("content://media/external/file/42")
 * ```
 */
class ContentResolverSharedDocumentReader(
  private val contentResolver: ContentResolver,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SharedDocumentReader {
  override suspend fun readBytes(uri: String): ByteArray = withContext(ioDispatcher) {
    val stream = contentResolver.openInputStream(Uri.parse(uri))
      ?: throw IllegalArgumentException("the provider of '$uri' served no stream; expected a readable document")
    stream.use { it.readBoundedBytes(SharedDocumentReader.MAX_BYTES) }
  }
}
