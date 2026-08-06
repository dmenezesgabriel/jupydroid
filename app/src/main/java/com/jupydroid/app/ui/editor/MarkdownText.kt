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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jupydroid.app.markdown.MarkdownBlock
import com.jupydroid.app.markdown.toAnnotatedString

/**
 * Draws parsed markdown as Compose, with no WebView and no HTML anywhere in the path.
 *
 * Recursive, because the model is: a quote holds blocks, a list item holds blocks, and either
 * may hold the other. Only leaves reach [Text].
 *
 * ```
 * MarkdownText(MarkdownDocument.parse(cell.source))
 * ```
 */
@Composable
fun MarkdownText(blocks: List<MarkdownBlock>, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
    blocks.forEach { MarkdownBlockText(it) }
  }
}

@Composable
private fun MarkdownBlockText(block: MarkdownBlock) {
  val styles = markdownInlineStyles()
  when (block) {
    is MarkdownBlock.Heading -> Text(block.inlines.toAnnotatedString(styles), style = headingStyle(block.level))
    is MarkdownBlock.Paragraph ->
      Text(block.inlines.toAnnotatedString(styles), style = MaterialTheme.typography.bodyMedium)
    is MarkdownBlock.CodeBlock -> CodeBlockText(block)
    is MarkdownBlock.BlockQuote -> BlockQuoteText(block)
    is MarkdownBlock.ListBlock -> ListBlockText(block)
    MarkdownBlock.ThematicBreak -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  }
}

/** Scrolls rather than wraps: a wrapped line of code is harder to read than one you drag. */
@Composable
private fun CodeBlockText(block: MarkdownBlock.CodeBlock) {
  Text(
    text = block.code.trimEnd('\n'),
    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
      .padding(8.dp)
      .horizontalScroll(rememberScrollState()),
  )
}

/**
 * The rule down the left is drawn rather than composed - a Divider would need its own row and a
 * height nobody knows until the quote is laid out. Jewel draws it the same way
 * (`DefaultMarkdownBlockRenderer.kt`).
 */
@Composable
private fun BlockQuoteText(block: MarkdownBlock.BlockQuote) {
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
    block.children.forEach { MarkdownBlockText(it) }
  }
}

@Composable
private fun ListBlockText(block: MarkdownBlock.ListBlock) {
  Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
    block.items.forEachIndexed { index, item ->
      Row {
        Text(
          text = if (block.ordered) "${block.startFrom + index}." else "•",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.width(MARKER_WIDTH),
        )
        Column(verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
          item.forEach { MarkdownBlockText(it) }
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

private val BLOCK_GAP = 8.dp
private val QUOTE_INDENT = 12.dp
private val QUOTE_RULE_WIDTH = 3.dp
private val MARKER_WIDTH = 24.dp
