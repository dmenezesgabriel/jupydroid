package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.richtext.plainTextWebLinks
import com.jupydroid.app.theme.LocalAnsiPalette
import com.jupydroid.app.theme.Spacing

/**
 * What a cell's outputs draw as - split out of [EditorScreen] on the file-length gate
 * (`AGENTS.md` rule 6). [RenderedOutput] decides *what* an output becomes; this is only how that
 * gets drawn, which is why the split sits at exactly that seam.
 */
@Composable
internal fun CellOutputs(
  outputs: List<CellOutput>,
  hidden: Boolean,
  /** `notebook:toggle-output-scrolling` - see [scrolledOutputsHeight]. */
  scrolled: Boolean,
  /** One entry per rendered output, or empty - see [outputSearchHighlightsOf]. */
  search: List<CellSearchHighlight>,
  onExpand: () -> Unit,
  /** What the run is blocked on, drawn after the outputs as a field - see [InputPromptField]. */
  inputRequest: KernelInputRequest?,
  onAnswerInput: (String) -> Unit,
) {
  RenderedOutputs(outputs, hidden, scrolled, search, onExpand)
  // Outside the fold and past the early return: `input("Name?")` has printed nothing yet, and a
  // collapsed output area must not hide a cell that is waiting on the user.
  inputRequest?.let { request ->
    Spacer(modifier = Modifier.height(Spacing.xs))
    InputPromptField(request, onSubmit = onAnswerInput, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun RenderedOutputs(
  outputs: List<CellOutput>,
  hidden: Boolean,
  scrolled: Boolean,
  search: List<CellSearchHighlight>,
  onExpand: () -> Unit,
) {
  val rendered = outputs.mapNotNull { it.render() }
  // Nothing to fold when there is nothing to show, so an empty cell looks the same either way.
  if (rendered.isEmpty()) return
  // Past the early return, so a re-run - which empties the outputs first - drops it, as it drops
  // `OutputText`'s own. Applied scrolled or not: the box bounds the height, not the drawing.
  var showAllOutputs by rememberSaveable { mutableStateOf(false) }
  val drawnCount = if (showAllOutputs) null else drawnOutputCountOf(outputs)
  // A prefix of the outputs renders to a prefix of `rendered`, so `search` keeps its indices.
  val drawn = drawnCount?.let { outputs.take(it).mapNotNull { output -> output.render() } } ?: rendered
  val onShowMore = drawnCount?.let { { showAllOutputs = true } }
  // Stepping onto a hit in an undrawn output draws the rest, as landing on a match opens a collapsed
  // output area (`withMatchRevealed`, app/src/main/java/com/jupydroid/app/ui/editor/CellReveal.kt):
  // search reads the model, and a counted match nobody can see is not a result. Latched, as that is.
  val currentHitUndrawn = drawnCount != null && isCurrentHitUndrawn(search, drawn.size)
  LaunchedEffect(currentHitUndrawn) { if (currentHitUndrawn) showAllOutputs = true }
  Spacer(modifier = Modifier.height(Spacing.xs))
  when {
    // Collapsed beats scrolled: there is no height to bound when nothing is drawn.
    hidden -> CollapsedCell(rendered.collapsedPreview(), COLLAPSED_OUTPUTS, onExpand)
    scrolled -> ScrolledOutputs(drawn, search, onShowMore)
    else -> OutputColumn(drawn, search, { outputLineWindowOf(it) }, onShowMore, modifier = Modifier.fillMaxWidth())
  }
}

/**
 * `notebook:enable-output-scrolling`'s look: `max-height: 24em; overflow-y: auto`, plus an inset
 * shadow that signals there is more below (`packages/cells/style/widget.css:72-95`).
 *
 * **`24em` is 24 line heights of the output's own font**, which is what `em` means - so the bound
 * follows the user's font size instead of freezing a `dp` that would either clip a larger font mid
 * character or leave room to spare on a smaller one. Measured the way [LineNumbers] measures a
 * digit, rather than converting [CodeText.lineHeight] from `sp`: this app has no existing
 * `TextUnit`-to-`Dp` conversion, and a `TextMeasurer` is already how a text metric becomes a
 * `Dp` here.
 *
 * `resize: vertical` is **not** built - a drag handle is not a touch idiom, and there is no
 * pointer to hover it into view with.
 */
@Composable
private fun ScrolledOutputs(
  rendered: List<RenderedOutput>,
  search: List<CellSearchHighlight>,
  onShowMoreOutputs: (() -> Unit)?,
) {
  Box(modifier = Modifier.fillMaxWidth().heightIn(max = scrolledOutputsHeight())) {
    OutputColumn(
      rendered,
      search,
      // The box bounds the height but not the layout, so it keeps a cut of its own: the last lines,
      // since scrolling down is how the box is read.
      { outputTailWindowOf(it) },
      onShowMoreOutputs,
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
  val style = codeTextStyle()
  val lineHeightPx = remember(style) { measurer.measure("0", style).size.height }
  return with(LocalDensity.current) { (lineHeightPx * SCROLLED_OUTPUT_LINES).toDp() }
}

@Composable
private fun OutputColumn(
  rendered: List<RenderedOutput>,
  search: List<CellSearchHighlight>,
  /** What of a long text is drawn - [outputLineWindowOf], or [outputTailWindowOf] in a scrolled box. */
  lineCut: (String) -> OutputLineWindow?,
  /** Set when outputs past [rendered] are left undrawn - see [drawnOutputCountOf]. */
  onShowMoreOutputs: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.semantics { contentDescription = "Cell output" }) {
    rendered.forEachIndexed { at, output ->
      when (output) {
        is RenderedOutput.Text -> OutputText(output, search.getOrElse(at) { CellSearchHighlight.NONE }, lineCut)
        is RenderedOutput.Image -> OutputImage(output)
        // The same renderer a markdown cell uses: `text/html` parses to the same blocks.
        is RenderedOutput.Document -> RichText(output.blocks, Modifier.fillMaxWidth())
      }
    }
    // Inside the column, where JupyterLab's `TrimmedOutputs` sits in the output area, so a scrolled
    // box scrolls to it.
    onShowMoreOutputs?.let { HiddenItemsRow(SHOW_MORE_OUTPUTS, onExpand = it) }
  }
}

@Composable
private fun OutputText(
  output: RenderedOutput.Text,
  search: CellSearchHighlight,
  lineCut: (String) -> OutputLineWindow?,
) {
  // An `AnnotatedString` rather than a second transformation: an output is drawn, never typed
  // into, so there is no `TextFieldBuffer` here and nothing to keep in step with a caret.
  // Found once per text rather than per frame: a streaming output recomposes on every chunk.
  val links = remember(output.text) { output.text.plainTextWebLinks() }
  val text = output.coloured(LocalAnsiPalette.current)
    .withWebLinks(links, richInlineStyles().link)
    .withSearchMatches(search, searchMatchStyles())
  // Per output and saveable, as JupyterLab's "Show more outputs" is per output area and lasts the
  // widget's life: a re-run empties the outputs first, which drops this, so the next run is cut again.
  var showAll by rememberSaveable { mutableStateOf(false) }
  val window = if (showAll) null else lineCut(output.text)
  // The line cut gives way to a stepped-onto hit as the count cut does - see `RenderedOutputs`.
  // Only `current`: typing a query counts its hits and draws none, as `startQuery` scrolls to none.
  val current = search.current?.takeIf { search.currentStepped }
  LaunchedEffect(window, current) { if (current != null && window?.hides(current) == true) showAll = true }
  val colors = MaterialTheme.colorScheme
  when (output.frame()) {
    OutputTextFrame.PLAIN -> WindowedText(text, window, colors.onSurface, Modifier) { showAll = true }
    // A traceback is long and needs to be findable at a glance, so it gets its own tinted
    // block rather than only a colour that a monospace wall of text swallows.
    OutputTextFrame.TINTED -> WindowedText(
      text,
      window,
      colors.onErrorContainer,
      Modifier
        .fillMaxWidth()
        .background(colors.errorContainer, MaterialTheme.shapes.small)
        .padding(Spacing.sm),
    ) { showAll = true }
    // Its own colours need the surface to read (see `frame`), so the rule does the tint's job.
    OutputTextFrame.RULED -> WindowedText(
      text,
      window,
      colors.onSurface,
      Modifier
        .fillMaxWidth()
        .drawBehind { drawRect(colors.error, size = Size(ERROR_RULE_WIDTH.toPx(), size.height)) }
        .padding(start = Spacing.sm),
    ) { showAll = true }
  }
}

/**
 * [text] whole, or only the [window]'s head and tail with the hidden count between them - a
 * scrolled box's window has no head, so the row sits above the tail.
 *
 * Cut **after** colouring and search highlighting, so the tail keeps the colour an escape in the
 * hidden middle set, which VS Code's renderer loses by parsing the tail alone
 * (vscode@3f3380c6 extensions/notebook-renderers/src/textHelper.ts, `truncatedArrayOfString`).
 * What the cut bounds is the layout - the cost that grows with every line a runaway prints.
 * Drawn whole, the text is one `Text` per chunk of lines - see [outputTextChunksOf].
 */
@Composable
private fun WindowedText(
  text: AnnotatedString,
  window: OutputLineWindow?,
  color: Color,
  modifier: Modifier,
  onShowAll: () -> Unit,
) {
  if (window == null) {
    Column(modifier = modifier) { ChunkedText(text, color) }
    return
  }
  Column(modifier = modifier) {
    if (window.headEnd > 0) Text(text = text.subSequence(0, window.headEnd), style = codeTextStyle(), color = color)
    HiddenItemsRow(hiddenLinesLabel(window.hiddenLines), onExpand = onShowAll)
    // Chunked as the whole text is: a scrolled box's tail is thousands of lines.
    ChunkedText(text.subSequence(window.tailStart, text.length), color)
  }
}

/** [text] as one `Text` per chunk of lines, stacked in the caller's column - see [outputTextChunksOf]. */
@Composable
private fun ChunkedText(text: AnnotatedString, color: Color) {
  outputTextChunksOf(text.text).forEach { chunk ->
    Text(text = text.subSequence(chunk.first, chunk.last + 1), style = codeTextStyle(), color = color)
  }
}

@Composable
private fun OutputImage(output: RenderedOutput.Image) {
  // Decoded here rather than in RenderedOutput so that stays free of Android types and
  // unit-testable; remembered by content so scrolling does not re-decode every frame.
  // `BitmapFactory` sniffs the format from the header, and decodes a GIF's first frame only - an
  // animated GIF draws still, where JupyterLab's `<img>` would play it.
  val bitmap = remember(output) {
    BitmapFactory.decodeByteArray(output.encoded, 0, output.encoded.size)?.asImageBitmap()
  } ?: return
  // JupyterLab's `<img>`: the width the kernel asked for, else the image's own with one pixel to a dp
  // as a WebView maps a CSS px, at the column's start. `width` is coerced by the column's max
  // constraint, which is `max-width: 100%`, and the ratio sets the height, which is `height: auto`
  // (jupyterlab@7f0e690 packages/rendermime/src/renderers.ts `renderImage`, style/base.css; ADR-0086).
  Image(
    bitmap = bitmap,
    contentDescription = "Cell image output",
    modifier = Modifier
      .width((output.width ?: bitmap.width).dp)
      .aspectRatio(bitmap.width / bitmap.height.toFloat()),
    contentScale = ContentScale.FillBounds,
  )
}

/** The coloured traceback's edge: the 2dp of a cell's edit-mode stripe (`CellStripe.kt`), an edge and not a frame. */
private val ERROR_RULE_WIDTH = 2.dp

/** 24 line heights - the reference's `max-height: 24em` on `.jp-mod-outputsScrolled`. */
private const val SCROLLED_OUTPUT_LINES = 24

/**
 * The gradient at the foot of a bounded output strip, which is what says the strip scrolls.
 * A height rather than a gap: it is the length the fade takes to reach the surface colour.
 */
private val SCROLL_FADE_HEIGHT = 12.dp
