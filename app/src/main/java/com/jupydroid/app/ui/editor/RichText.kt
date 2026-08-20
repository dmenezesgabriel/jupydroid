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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.toAnnotatedString
import com.jupydroid.app.theme.Spacing

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
 */
@Composable
fun RichText(blocks: List<RichBlock>, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
    blocks.forEach { RichBlockText(it) }
  }
}

@Composable
private fun RichBlockText(block: RichBlock) {
  val styles = richInlineStyles()
  when (block) {
    is RichBlock.Heading -> Text(block.inlines.toAnnotatedString(styles), style = headingStyle(block.level))
    is RichBlock.Paragraph ->
      Text(block.inlines.toAnnotatedString(styles), style = richTextProseStyle())
    is RichBlock.CodeBlock -> CodeBlockText(block)
    is RichBlock.BlockQuote -> BlockQuoteText(block)
    is RichBlock.ListBlock -> ListBlockText(block)
    RichBlock.ThematicBreak -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    is RichBlock.Table -> RichTable(block)
  }
}

/** Scrolls rather than wraps: a wrapped line of code is harder to read than one you drag. */
@Composable
private fun CodeBlockText(block: RichBlock.CodeBlock) {
  Text(
    text = block.code.trimEnd('\n'),
    style = richTextCodeStyle(),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
      .padding(Spacing.sm)
      .horizontalScroll(rememberScrollState()),
  )
}

/**
 * The rule down the left is drawn rather than composed - a Divider would need its own row and a
 * height nobody knows until the quote is laid out. Jewel draws it the same way
 * (`DefaultMarkdownBlockRenderer.kt`).
 */
@Composable
private fun BlockQuoteText(block: RichBlock.BlockQuote) {
  val rule = MaterialTheme.colorScheme.outline
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .drawBehind {
        val x = QUOTE_RULE_WIDTH.toPx() / 2
        drawLine(rule, Offset(x, 0f), Offset(x, size.height), QUOTE_RULE_WIDTH.toPx())
      }
      .padding(start = QUOTE_INDENT),
    verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
  ) {
    block.children.forEach { RichBlockText(it) }
  }
}

@Composable
private fun ListBlockText(block: RichBlock.ListBlock) {
  Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
    block.items.forEachIndexed { index, item ->
      Row {
        Text(
          text = if (block.ordered) "${block.startFrom + index}." else "•",
          style = richTextProseStyle(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(MARKER_WIDTH),
        )
        Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
          item.forEach { RichBlockText(it) }
        }
      }
    }
  }
}

/** Markdown allows six levels; past the third they differ by weight rather than by size. */
@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
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
