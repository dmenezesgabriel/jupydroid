package com.jupydroid.app.ui.editor

import androidx.compose.ui.platform.UriHandler
import com.jupydroid.app.richtext.headingLinkFragment

/**
 * A tapped link that is a bare `#fragment`, handed decoded to [follow] to find in this document;
 * every other destination goes on to [outer]. A path's own fragment is not one of these: the link
 * names another file, and goes to it.
 *
 * ```
 * HeadingLinkUriHandler(workspaceLinks) { fragment -> jumpToHeadingAnchor(fragment) }
 * ```
 */
internal class HeadingLinkUriHandler(private val outer: UriHandler, private val follow: (fragment: String) -> Unit) :
  UriHandler {
  override fun openUri(uri: String) {
    val fragment = uri.headingLinkFragment() ?: return outer.openUri(uri)
    follow(fragment)
  }
}

/** What a `#fragment` no heading answers to says, naming it as the link wrote it, decoded. */
internal fun missingHeadingMessage(fragment: String): String = "No heading #$fragment"
