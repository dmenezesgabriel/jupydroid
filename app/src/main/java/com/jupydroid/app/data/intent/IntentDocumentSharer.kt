package com.jupydroid.app.data.intent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jupydroid.app.data.disk.WorkspaceRoot
import com.jupydroid.app.domain.port.DocumentSharer
import java.io.File

/**
 * Shares a workspace file through Android's chooser, over a [FileProvider] `content://` URI.
 *
 * **A `file://` URI cannot be used and is not a matter of taste**: passing one out of an app has
 * thrown `FileUriExposedException` since Android 7 (targetSdk 24), and the workspace lives in this
 * app's private storage where no other app could read it anyway. The provider is what grants a
 * one-shot read to whichever app the user picks, which is why [Intent.FLAG_GRANT_READ_URI_PERMISSION]
 * is on the intent and the authority is declared in the manifest.
 *
 * ```
 * IntentDocumentSharer(context, notebookWorkspace(context)).share("Analysis.html", "text/html")
 * ```
 */
class IntentDocumentSharer(
  private val context: Context,
  /** The workspace root, since every path this port speaks is relative to it. */
  private val workspace: File,
  /**
   * Must match the manifest's `${applicationId}.exports`. Read off the context rather than
   * imported from `BuildConfig`, which is generated per build type - a mismatched authority fails
   * at the moment of sharing rather than at build time.
   */
  private val authority: String = "${context.packageName}.exports",
) : DocumentSharer {
  /**
   * The URI a receiving app is handed for [path].
   *
   * Its own function so it can be tested: everything that can go wrong with sharing goes wrong
   * here - an authority that does not match the manifest, or a workspace the provider's
   * `<files-path>` does not cover - and all of it is framework configuration that no JVM test can
   * reach. `getUriForFile` throws `IllegalArgumentException` for a file outside every configured
   * root, which is the check rather than a silent wrong answer.
   *
   * The provider's root is every Space's directory (`res/xml/exported_documents.xml`), which also
   * holds each Space's rootfs. So the workspace is checked here first: `fileAt` throws
   * `IllegalArgumentException` for any [path] that leaves it, the same type the provider throws.
   */
  fun uriFor(path: String): Uri = FileProvider.getUriForFile(context, authority, WorkspaceRoot(workspace).fileAt(path))

  override fun share(path: String, mediaType: String) {
    val uri = uriFor(path)
    val send = Intent(Intent.ACTION_SEND).apply {
      type = mediaType
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // `createChooser` rather than the bare intent, because an export is a one-off act and a
    // default app for it would be a choice the user never made. NEW_TASK because the container
    // holds the *application* context - the composition's activity would tie the chooser to a task
    // that a rotation recreates.
    context.startActivity(
      Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }
}
