package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.CellOutput

/**
 * What a cell's outputs draw as - split out of [EditorScreen] on the file-length gate
 * (`CLAUDE.md` rule 6). [RenderedOutput] decides *what* an output becomes; this is only how that
 * gets drawn, which is why the split sits at exactly that seam.
 */
/** What each output kind becomes on screen is [RenderedOutput]'s decision, not this one's. */
@Composable
internal fun CellOutputs(outputs: List<CellOutput>, hidden: Boolean, onExpand: () -> Unit) {
  val rendered = outputs.mapNotNull { it.render() }
  // Nothing to fold when there is nothing to show, so an empty cell looks the same either way.
  if (rendered.isEmpty()) return
  Spacer(modifier = Modifier.height(4.dp))
  if (hidden) {
    CollapsedCell(rendered.collapsedPreview(), COLLAPSED_OUTPUTS, onExpand)
    return
  }
  Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Cell output" }) {
    rendered.forEach { output ->
      when (output) {
        is RenderedOutput.Text -> OutputText(output)
        is RenderedOutput.Image -> OutputImage(output)
        // The same renderer a markdown cell uses: `text/html` parses to the same blocks.
        is RenderedOutput.Document -> RichText(output.blocks, Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun OutputText(output: RenderedOutput.Text) {
  if (!output.isError) {
    Text(
      text = output.text,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurface,
    )
    return
  }
  // A traceback is long and needs to be findable at a glance, so it gets its own tinted
  // block rather than only a colour that a monospace wall of text swallows.
  Text(
    text = output.text,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = FontFamily.Monospace,
    color = MaterialTheme.colorScheme.onErrorContainer,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
      .padding(8.dp),
  )
}

@Composable
private fun OutputImage(output: RenderedOutput.Image) {
  // Decoded here rather than in RenderedOutput so that stays free of Android types and
  // unit-testable; remembered by content so scrolling does not re-decode every frame.
  val bitmap = remember(output) {
    BitmapFactory.decodeByteArray(output.png, 0, output.png.size)?.asImageBitmap()
  } ?: return
  Image(
    bitmap = bitmap,
    contentDescription = "Cell image output",
    modifier = Modifier.fillMaxWidth(),
    contentScale = ContentScale.Fit,
  )
}

