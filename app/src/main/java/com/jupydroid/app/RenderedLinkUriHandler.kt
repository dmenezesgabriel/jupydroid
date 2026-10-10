package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.jupydroid.app.domain.port.ExternalLinkOpener
import com.jupydroid.app.richtext.isWebLinkDestination

/**
 * Where a tap on a rendered link goes: a web or mail address to [externalLinkOpener], and anything
 * else nowhere.
 *
 * A [UriHandler] because that is what Compose's `Text` hands a tapped `LinkAnnotation.Url` to, so
 * no renderer between the link and this class carries a callback for it. The platform's own
 * handler is not used: it passes every destination to `startActivity` unjudged.
 *
 * ```
 * RenderedLinkUriHandler(appContainer.externalLinkOpener).openUri("https://jupyter.org")
 * ```
 */
internal class RenderedLinkUriHandler(private val externalLinkOpener: ExternalLinkOpener) : UriHandler {
  override fun openUri(uri: String) {
    if (uri.isWebLinkDestination()) externalLinkOpener.open(uri)
  }
}

/**
 * Makes every link drawn inside [content] follow [RenderedLinkUriHandler]'s rule.
 *
 * ```
 * RenderedLinksFollowed(appContainer.externalLinkOpener) { MainNavigation(appContainer, ...) }
 * ```
 */
@Composable
internal fun RenderedLinksFollowed(externalLinkOpener: ExternalLinkOpener, content: @Composable () -> Unit) {
  val links = remember(externalLinkOpener) { RenderedLinkUriHandler(externalLinkOpener) }
  CompositionLocalProvider(LocalUriHandler provides links, content = content)
}
