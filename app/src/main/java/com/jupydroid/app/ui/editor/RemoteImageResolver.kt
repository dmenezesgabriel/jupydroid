package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.jupydroid.app.domain.port.RemoteImageReader
import com.jupydroid.app.richtext.isWebImageSource

/**
 * An `https:` image source fetched through [remoteImageReader]; every other source goes on to
 * [outer]. A web address that serves nothing is nothing, so its alt draws.
 *
 * ```
 * RemoteImageResolver(appContainer.remoteImageReader, outer).imageBytes("https://jupyter.org/logo.png")
 * ```
 */
class RemoteImageResolver(private val remoteImageReader: RemoteImageReader, private val outer: RichImageResolver) :
  RichImageResolver {
  override suspend fun imageBytes(source: String): ByteArray? =
    if (source.isWebImageSource()) remoteImageReader.imageBytes(source) else outer.imageBytes(source)
}

/**
 * Makes images drawn inside [content] that name a web address fetch it, around whatever resolver
 * is already provided. A web address means the same in every document, so this sits at the root.
 *
 * ```
 * RemoteImagesResolved(appContainer.remoteImageReader) { MainNavigation(...) }
 * ```
 */
@Composable
fun RemoteImagesResolved(remoteImageReader: RemoteImageReader, content: @Composable () -> Unit) {
  val outer = LocalRichImageResolver.current
  val resolver = remember(remoteImageReader, outer) { RemoteImageResolver(remoteImageReader, outer) }
  CompositionLocalProvider(LocalRichImageResolver provides resolver, content = content)
}
