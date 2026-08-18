package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.CellOutput

/**
 * What a cell's outputs draw as - split out of [EditorScreen] on the file-length gate
 * (`CLAUDE.md` rule 6). [RenderedOutput] decides *what* an output becomes; this is only how that
 * gets drawn, which is why the split sits at exactly that seam.
 */
@Composable
internal fun CellOutputs(
  outputs: List<CellOutput>,
  hidden: Boolean,
  /** `notebook:toggle-output-scrolling` - see [scrolledOutputsHeight]. */
  scrolled: Boolean,
  onExpand: () -> Unit,
) {
  val rendered = outputs.mapNotNull { it.render() }
  // Nothing to fold when there is nothing to show, so an empty cell looks the same either way.
  if (rendered.isEmpty()) return
  Spacer(modifier = Modifier.height(4.dp))
  when {
    // Collapsed beats scrolled: there is no height to bound when nothing is drawn.
    hidden -> CollapsedCell(rendered.collapsedPreview(), COLLAPSED_OUTPUTS, onExpand)
    scrolled -> ScrolledOutputs(rendered)
    else -> OutputColumn(rendered, modifier = Modifier.fillMaxWidth())
  }
}

/**
 * `notebook:enable-output-scrolling`'s look: `max-height: 24em; overflow-y: auto`, plus an inset
 * shadow that signals there is more below (`packages/cells/style/widget.css:72-95`).
 *
 * **`24em` is 24 line heights of the output's own font**, which is what `em` means - so the bound
 * follows the user's font size instead of freezing a `dp` that would either clip a larger font mid
 * character or leave room to spare on a smaller one. Measured the way [LineNumbers] measures a
 * digit, rather than converting `bodySmall.lineHeight` from `sp`: this app has no existing
 * `TextUnit`-to-`Dp` conversion, and a `TextMeasurer` is already how a text metric becomes a
 * `Dp` here.
 *
 * `resize: vertical` is **not** built - a drag handle is not a touch idiom, and there is no
 * pointer to hover it into view with. Recorded as a departure in `docs/DECISIONS.md`.
 */
@Composable
private fun ScrolledOutputs(rendered: List<RenderedOutput>) {
  Box(modifier = Modifier.fillMaxWidth().heightIn(max = scrolledOutputsHeight())) {
    OutputColumn(
      rendered,
      // verticalScroll bounds the Column's own *layout* size but draws the overflow past it
      // rather than cropping it - clipToBounds is what makes the bound a visual one too, and
      // without it the tail of a tall output is still on screen, just past what a user would
      // call the box.
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).clipToBounds(),
    )
    // The reference's `::after` shadow: a fade rather than a hard edge, so "there is more" reads
    // as a hint and not as a second border competing with the cell's own frame.
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(SCROLL_FADE_HEIGHT)
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
          ),
        ),
    )
  }
}

@Composable
private fun scrolledOutputsHeight(): Dp {
  val measurer = rememberTextMeasurer()
  val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
  val lineHeightPx = remember(style) { measurer.measure("0", style).size.height }
  return with(LocalDensity.current) { (lineHeightPx * SCROLLED_OUTPUT_LINES).toDp() }
}

@Composable
private fun OutputColumn(rendered: List<RenderedOutput>, modifier: Modifier = Modifier) {
  Column(modifier = modifier.semantics { contentDescription = "Cell output" }) {
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

/** 24 line heights - the reference's `max-height: 24em` on `.jp-mod-outputsScrolled`. */
private const val SCROLLED_OUTPUT_LINES = 24

private val SCROLL_FADE_HEIGHT = 12.dp
