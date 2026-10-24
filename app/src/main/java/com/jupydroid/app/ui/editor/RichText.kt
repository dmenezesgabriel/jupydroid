package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette

/**
 * Draws a parsed document as Compose, with no WebView and no HTML anywhere in the path.
 *
 * Recursive, because the model is: a quote holds blocks, a list item holds blocks, and either
 * may hold the other. Only leaves reach [Text].
 *
 * One renderer for both front ends - markdown cells and `text/html` outputs parse to the same
 * [RichBlock]s, so there is nothing language-specific left by the time it gets here.
 *
 * ```
 * RichText(MarkdownDocument.parse(cell.source))
 * ```
 *
 * @param codeBlockActions drawn under every code block, a nested one too, and handed its code as
 *   shown; nothing by default, as a markdown cell draws nothing there.
 */
@Composable
fun RichText(
  blocks: List<RichBlock>,
  modifier: Modifier = Modifier,
  codeBlockActions: @Composable (code: String) -> Unit = {},
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
    blocks.forEach { RichBlockText(it, codeBlockActions) }
  }
}

@Composable
private fun RichBlockText(block: RichBlock, codeBlockActions: @Composable (code: String) -> Unit) {
  val styles = richInlineStyles()
  when (block) {
    is RichBlock.Heading -> RichInlineText(block.inlines, styles, headingStyle(block.level))
    is RichBlock.Paragraph -> RichInlineText(block.inlines, styles, richTextProseStyle())
    is RichBlock.CodeBlock -> CodeBlockText(block, codeBlockActions)
    is RichBlock.BlockQuote -> BlockQuoteText(block, codeBlockActions)
    is RichBlock.ListBlock -> ListBlockText(block, codeBlockActions)
    RichBlock.ThematicBreak -> HorizontalDivider(color = LocalContentColor.current)
    is RichBlock.Table -> RichTable(block)
  }
}

/** Scrolls rather than wraps: a wrapped line of code is harder to read than one you drag. */
@Composable
private fun CodeBlockText(block: RichBlock.CodeBlock, codeBlockActions: @Composable (code: String) -> Unit) {
  val code = block.code.trimEnd('\n')
  Column {
    Text(
      text = code,
      style = richTextCodeStyle(),
      color = editorPalette.preformatForeground,
      modifier = Modifier
        .fillMaxWidth()
        .background(editorPalette.preformatBackground, MaterialTheme.shapes.small)
        .padding(Spacing.sm)
        .horizontalScroll(rememberScrollState()),
    )
    codeBlockActions(code)
  }
}

/**
 * The rule down the left is drawn rather than composed - a Divider would need its own row and a
 * height nobody knows until the quote is laid out. Jewel draws it the same way
 * (`DefaultMarkdownBlockRenderer.kt`).
 */
@Composable
private fun BlockQuoteText(block: RichBlock.BlockQuote, codeBlockActions: @Composable (code: String) -> Unit) {
  val rule = editorPalette.blockQuoteBorder
  val wash = editorPalette.blockQuoteBackground
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(wash)
      .drawBehind {
        val x = QUOTE_RULE_WIDTH.toPx() / 2
        drawLine(rule, Offset(x, 0f), Offset(x, size.height), QUOTE_RULE_WIDTH.toPx())
      }
      .padding(start = QUOTE_INDENT),
    verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
  ) {
    block.children.forEach { RichBlockText(it, codeBlockActions) }
  }
}

@Composable
private fun ListBlockText(block: RichBlock.ListBlock, codeBlockActions: @Composable (code: String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
    block.items.forEachIndexed { index, item ->
      Row {
        Text(
          text = if (block.ordered) "${block.startFrom + index}." else "•",
          style = richTextProseStyle(),
          modifier = Modifier.width(MARKER_WIDTH),
        )
        Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
          item.forEach { RichBlockText(it, codeBlockActions) }
        }
      }
    }
  }
}

/**
 * Markdown allows six levels; past the third they differ by weight rather than by size.
 *
 * **Internal because the caret beside a heading has to centre on the same line this lays out.**
 * The reference does the same thing with six `[data-heading-level]` rules that each restate that
 * level's font size (`packages/cells/style/widget.css:184-213`), with a comment saying they must
 * stay identical to `packages/rendermime/style/base.css`. One `when` is that, kept honest.
 */
@Composable
internal fun headingStyle(level: Int): TextStyle = when (level) {
  HEADING_1 -> MaterialTheme.typography.headlineSmall
  HEADING_2 -> MaterialTheme.typography.titleLarge
  HEADING_3 -> MaterialTheme.typography.titleMedium
  else -> MaterialTheme.typography.titleSmall
}

private const val HEADING_1 = 1
private const val HEADING_2 = 2
private const val HEADING_3 = 3

private val BLOCK_GAP = Spacing.sm
private val QUOTE_INDENT = Spacing.md

/**
 * The bar down the left of a block quote. Wider than a hairline because it is the only thing
 * marking the quote - there is no background behind one - and thinner than a cell stripe so the
 * two do not read as the same kind of mark.
 */
private val QUOTE_RULE_WIDTH = 3.dp

/**
 * The column a list marker is drawn in, fixed so that every item's text starts at the same x
 * whatever its marker is - `1.` and `10.` are different widths and a ragged left edge is what a
 * fixed column exists to prevent.
 */
private val MARKER_WIDTH = 24.dp
