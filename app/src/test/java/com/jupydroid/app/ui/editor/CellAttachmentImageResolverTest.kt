package com.jupydroid.app.ui.editor

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * `attachment:name` answered from the cell's own bundle, as `AttachmentsResolver.getDownloadUrl`
 * answers it (jupyterlab@7f0e690 packages/attachments/src/model.ts), and every other source passed
 * on to the resolver around it.
 */
class CellAttachmentImageResolverTest {
  private val outer = SourceRecordingImageResolver()

  @Test
  fun anAttachmentIsItsDecodedBytes() = runTest {
    val resolver = resolverOver("""{"chart.png": {"image/png": "$PNG"}}""")

    assertArrayEquals(PNG_BYTES, resolver.imageBytes("attachment:chart.png"))
  }

  /** Lab resolves a missing one to its unprocessed path and draws it broken; here its alt draws. */
  @Test
  fun aMissingAttachmentIsNothing() = runTest {
    assertNull(resolverOver("""{"chart.png": {"image/png": "$PNG"}}""").imageBytes("attachment:other.png"))
    assertNull(CellAttachmentImageResolver(null, outer).imageBytes("attachment:chart.png"))
  }

  /** Lab throws for a mime `imageRendererFactory` does not draw; nothing else asks for one either. */
  @Test
  fun anAttachmentThatIsNotAnImageIsNothing() = runTest {
    assertNull(resolverOver("""{"a.txt": {"text/plain": "aGk="}}""").imageBytes("attachment:a.txt"))
  }

  /** The first mime key is the attachment, as Lab and VS Code both read it. */
  @Test
  fun theFirstMimeKeyIsTheOneRead() = runTest {
    val resolver = resolverOver("""{"a": {"text/plain": "aGk=", "image/png": "$PNG"}}""")

    assertNull(resolver.imageBytes("attachment:a"))
  }

  @Test
  fun anUndecodablePayloadIsNothing() = runTest {
    assertNull(resolverOver("""{"a.png": {"image/png": "not base64!"}}""").imageBytes("attachment:a.png"))
  }

  /** VS Code decodes the name (`cellAttachmentRenderer.ts`), so `my%20chart.png` finds `my chart.png`. */
  @Test
  fun anEscapedNameFindsTheAttachment() = runTest {
    val resolver = resolverOver("""{"my chart.png": {"image/png": "$PNG"}}""")

    assertArrayEquals(PNG_BYTES, resolver.imageBytes("attachment:my%20chart.png"))
  }

  /** VS Code's `tryDecodeURIComponent` falls back to the name as written, which is Lab's read. */
  @Test
  fun aMalformedEscapeIsReadAsWritten() = runTest {
    val resolver = resolverOver("""{"100%.png": {"image/png": "$PNG"}}""")

    assertArrayEquals(PNG_BYTES, resolver.imageBytes("attachment:100%.png"))
  }

  @Test
  fun anyOtherSourceGoesToTheResolverAroundIt() = runTest {
    val resolver = resolverOver("""{"a.png": {"image/png": "$PNG"}}""")

    assertNull(resolver.imageBytes("img/a.png"))
    assertEquals(listOf("img/a.png"), outer.asked)
  }

  private fun resolverOver(attachments: String): CellAttachmentImageResolver =
    CellAttachmentImageResolver(Json.parseToJsonElement(attachments).jsonObject, outer)

  private companion object {
    const val PNG = "iVBORw0KGgo="
    val PNG_BYTES: ByteArray = Base64.getDecoder().decode(PNG)
  }
}
