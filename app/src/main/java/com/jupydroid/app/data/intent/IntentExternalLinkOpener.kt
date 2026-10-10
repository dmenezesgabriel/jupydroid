package com.jupydroid.app.data.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jupydroid.app.domain.port.ExternalLinkOpener

/**
 * Opens a link's destination in whichever app the device views it with, through `ACTION_VIEW`.
 *
 * The bare intent rather than a chooser, unlike [IntentDocumentSharer]: a link is followed many
 * times, and the browser the user already made the default is the answer they gave.
 *
 * ```
 * IntentExternalLinkOpener(context).open("https://jupyter.org")
 * ```
 */
class IntentExternalLinkOpener(private val context: Context) : ExternalLinkOpener {
  /**
   * The intent [open] starts for [destination]. Its own function so what is asked of the device can
   * be read without a browser taking the screen. NEW_TASK because the container holds the
   * *application* context, which can start an activity no other way.
   */
  fun viewIntentFor(destination: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(destination)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  override fun open(destination: String) {
    try {
      context.startActivity(viewIntentFor(destination))
    } catch (_: ActivityNotFoundException) {
      // Nothing on this device views it. The port's contract is that nothing opens, as Termux's
      // `ShareUtils.openUrl` treats the same refusal (`termux-app@3df69d1d
      // termux-shared/src/main/java/com/termux/shared/interact/ShareUtils.java`).
    }
  }
}
