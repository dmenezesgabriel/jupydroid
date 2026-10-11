package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.jupydroid.app.domain.model.PreservedJson
import com.jupydroid.app.richtext.percentDecoded
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The bytes an image's source names, or null when it names nothing this resolver can read.
 *
 * Suspending because a source can name a file or a web address, and neither is read on the main
 * thread. Resolvers nest the way `UriHandler`s do for links (`WorkspaceLinkUriHandler`): each answers
 * the sources it owns and hands the rest to the one around it - the chain JupyterLab builds from
 * `IRenderMime.IResolver`s (jupyterlab@7f0e690 packages/attachments/src/model.ts, `AttachmentsResolver`).
 *
 * ```
 * LocalRichImageResolver.current.imageBytes("attachment:chart.png")
 * ```
 */
fun interface RichImageResolver {
  suspend fun imageBytes(source: String): ByteArray?
}

/** What a rendered document's images resolve through; the default reads nothing, so every image draws its alt. */
val LocalRichImageResolver = staticCompositionLocalOf { RichImageResolver { null } }

/**
 * `attachment:<name>` answered from a cell's own `attachments`, every other source passed to [outer].
 *
 * `AttachmentsResolver.getDownloadUrl`'s rule: the bundle's first mime key is the attachment, and
 * only an image mime is read. The name is percent-decoded as VS Code decodes it, and read as written
 * when an escape is malformed - which is what JupyterLab, which never decodes, reads
 * (vscode@3f3380c6 extensions/ipynb/notebook-src/cellAttachmentRenderer.ts, `tryDecodeURIComponent`).
 *
 * ```
 * CellAttachmentImageResolver(cell.preserved["attachments"] as? JsonObject, outer)
 * ```
 */
class CellAttachmentImageResolver(private val attachments: JsonObject?, private val outer: RichImageResolver) :
  RichImageResolver {
  override suspend fun imageBytes(source: String): ByteArray? {
    if (!source.startsWith(ATTACHMENT_SCHEME)) return outer.imageBytes(source)
    val name = source.removePrefix(ATTACHMENT_SCHEME)
    return attachmentImageBytes(name.percentDecoded() ?: name)
  }

  private fun attachmentImageBytes(name: String): ByteArray? {
    val (mimeType, payload) = (attachments?.get(name) as? JsonObject)?.entries?.firstOrNull() ?: return null
    return (payload as? JsonPrimitive)?.takeIf { mimeType in IMAGE_MIME_TYPES && it.isString }?.content?.decodedBase64()
  }
}

/**
 * Makes `attachment:` images drawn inside [content] read from [preserved]'s `attachments` - the cell's,
 * kept verbatim by `PreservedJson` - around whatever resolver the screen already provides.
 *
 * ```
 * CellAttachmentImagesResolved(cell.preserved) { MarkdownCell(...) }
 * ```
 */
@Composable
fun CellAttachmentImagesResolved(preserved: PreservedJson, content: @Composable () -> Unit) {
  val outer = LocalRichImageResolver.current
  val resolver = remember(preserved, outer) {
    CellAttachmentImageResolver(preserved["attachments"] as? JsonObject, outer)
  }
  CompositionLocalProvider(LocalRichImageResolver provides resolver, content = content)
}

private const val ATTACHMENT_SCHEME = "attachment:"
