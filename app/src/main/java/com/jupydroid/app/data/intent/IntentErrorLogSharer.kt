package com.jupydroid.app.data.intent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jupydroid.app.data.disk.errorLogDayFiles
import com.jupydroid.app.domain.port.ErrorLogSharer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Under `cacheDir`, which `res/xml/exported_documents.xml` names as `shared-logs/`; the two must agree. */
private const val BUNDLE_DIRECTORY = "shared-logs"

private const val BUNDLE_NAME = "error-log.jsonl"

/**
 * Shares the error log through Android's chooser: every day's file copied, oldest day first, into one file in
 * `cacheDir`, and that file handed out over a [FileProvider] `content://` URI.
 *
 * One bundle rather than the newest day alone, so what a person shares is what Settings counts. It is a copy
 * in the cache rather than the log's own files because the provider would otherwise need a root over the
 * external directory, which is wider than one file. Entries were redacted when they were written
 * ([com.jupydroid.app.data.disk.DiskErrorLog]), so the copy needs no second pass.
 *
 * ```
 * IntentErrorLogSharer(context, context.getExternalFilesDir(null)!!).share()
 * ```
 */
class IntentErrorLogSharer(
  private val context: Context,
  private val logDirectory: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ErrorLogSharer {
  /**
   * The URI of a fresh bundle of the log as it is now. Its own function so a test can read what the provider
   * serves: an authority that does not match the manifest fails only when the chooser opens.
   */
  fun bundleUri(): Uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", writeBundle())

  override suspend fun share() {
    val uri = withContext(ioDispatcher) { bundleUri() }
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "application/x-ndjson"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // NEW_TASK because the container holds the application context, as in `IntentDocumentSharer.share`.
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  /**
   * Replaces the last bundle, so the cache holds one. Written line by line: a day torn mid-line by a crash
   * ends without a newline, and a plain copy would join its last line to the next day's first.
   */
  private fun writeBundle(): File {
    val bundle = File(File(context.cacheDir, BUNDLE_DIRECTORY).also { it.mkdirs() }, BUNDLE_NAME)
    bundle.bufferedWriter().use { out ->
      errorLogDayFiles(logDirectory).sortedBy(File::getName).forEach { day ->
        day.forEachLine { line -> if (line.isNotBlank()) out.write(line + "\n") }
      }
    }
    return bundle
  }
}
