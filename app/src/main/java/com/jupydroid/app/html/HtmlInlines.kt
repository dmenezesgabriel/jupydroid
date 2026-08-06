package com.jupydroid.app.html

import com.jupydroid.app.richtext.RichInline
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * The inline half of [HtmlDocument]: what goes *inside* a block, and the whitespace rules that
 * decide where one starts and ends.
 *
 * Separate from the block walk because it is a separate question - the block walk asks what
 * shape the document has, this asks what the text looks like - and because both halves need
 * [isIgnored], which neither should be able to forget.
 */
internal fun Element.childInlines(): List<RichInline> =
  childNodes().filterNot { it.isIgnored() }.flatMap { it.asInlines() }.trimEdges()

internal fun Node.asInlines(): List<RichInline> = when (this) {
  is TextNode -> listOfNotNull(text().takeIf { it.isNotEmpty() }?.let(RichInline::Text))
  is Element -> markedInlines()
  else -> emptyList()
}

private fun Element.markedInlines(): List<RichInline> = when (tagName()) {
  "b", "strong" -> listOf(RichInline.StrongEmphasis(childInlines()))
  "i", "em", "cite", "var" -> listOf(RichInline.Emphasis(childInlines()))
  "code", "kbd", "samp", "tt" -> listOf(RichInline.Code(wholeText()))
  "a" -> listOf(RichInline.Link(attr("href"), childInlines()))
  "br" -> listOf(RichInline.HardLineBreak)
  // Anything unrecognised - span, font, u, sup - contributes its content and no mark of its
  // own, which is what dropping an unsupported wrapper should look like.
  else -> childInlines()
}

/**
 * `<style>` and `<script>` hold text that is not content: pandas wraps every DataFrame in a
 * `<style scoped>` block, and a walk that treats unknown elements as text puts its CSS on screen.
 *
 * `table` is here for a different reason - there is no table block yet, and the inline walk
 * would run every cell together into one paragraph, which is worse than the `text/plain` grid
 * a DataFrame ships beside it. Ignoring it leaves nothing to draw, so `RenderedOutput` falls
 * through to that grid, which is what the app already showed. It leaves this set the moment
 * there is a block to move it to.
 */
internal fun Node.isIgnored(): Boolean =
  this is Comment || (this is Element && tagName() in IGNORED_TAGS)

/**
 * HTML collapses runs of whitespace and drops it at a block's edges, so `<p>\n  hi\n</p>`
 * reads `hi`. jsoup's `TextNode.text()` does the collapsing; the edges are ours - and without
 * them the indentation between two tags becomes a paragraph of one space.
 */
internal fun List<RichInline>.trimEdges(): List<RichInline> {
  val kept = dropWhile { it.isBlankText() }.dropLastWhile { it.isBlankText() }.toMutableList()
  if (kept.isEmpty()) return emptyList()
  kept[0] = kept.first().mapText(String::trimStart)
  kept[kept.lastIndex] = kept.last().mapText(String::trimEnd)
  return kept
}

private fun RichInline.isBlankText(): Boolean = this is RichInline.Text && text.isBlank()

private fun RichInline.mapText(transform: (String) -> String): RichInline =
  if (this is RichInline.Text) RichInline.Text(transform(text)) else this

private val IGNORED_TAGS =
  setOf("style", "script", "head", "title", "meta", "link", "table")
