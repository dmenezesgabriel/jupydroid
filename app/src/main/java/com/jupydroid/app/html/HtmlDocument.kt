package com.jupydroid.app.html

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/**
 * A `text/html` output into [RichBlock]s - the same model a markdown cell parses to, so the
 * same Compose renderer draws it and no WebView is involved.
 *
 * jsoup is used for the tree only; `Element.html()` and the whole serialising half of it are
 * never touched, exactly as `org.commonmark.renderer` is never touched on the markdown side.
 * Its lenient tree builder is the point: what a kernel sends is whatever some library chose
 * to emit, and it must degrade rather than throw.
 *
 * ```
 * HtmlDocument.parse("<h3>Results</h3>")  // [Heading(level = 3, …)]
 * ```
 */
object HtmlDocument {
  fun parse(html: String): List<RichBlock> = Jsoup.parseBodyFragment(html).body().blocks()

  /**
   * Children into blocks, with any run of loose inlines gathered into a paragraph of its own.
   * That is what a browser does with `<div>text<p>para</p></div>`, and the only way
   * `display(HTML("hello"))` draws at all.
   */
  private fun Element.blocks(): List<RichBlock> {
    val blocks = mutableListOf<RichBlock>()
    val loose = mutableListOf<RichInline>()
    childNodes().filterNot { it.isIgnored() }.forEach { node ->
      val nested = node.asBlocks()
      if (nested == null) {
        loose += node.asInlines()
      } else {
        blocks += loose.takeParagraph()
        blocks += nested
      }
    }
    return blocks + loose.takeParagraph()
  }

  /** Null means "this is inline"; an empty list means a container that held nothing. */
  private fun Node.asBlocks(): List<RichBlock>? {
    val element = this as? Element ?: return null
    return when (val tag = element.tagName()) {
      "h1", "h2", "h3", "h4", "h5", "h6" ->
        listOf(RichBlock.Heading(tag.last().digitToInt(), element.childInlines()))
      "p" -> listOf(RichBlock.Paragraph(element.childInlines()))
      "pre" -> listOf(RichBlock.CodeBlock(element.wholeText().trim('\n'), language = null))
      "blockquote" -> listOf(RichBlock.BlockQuote(element.blocks()))
      "ul" -> listOf(RichBlock.ListBlock(ordered = false, startFrom = 1, items = element.items()))
      "ol" -> listOf(RichBlock.ListBlock(ordered = true, startFrom = element.startFrom(), items = element.items()))
      "hr" -> listOf(RichBlock.ThematicBreak)
      "table" -> element.asTable()
      // A container has no shape of its own; its children flatten into the document.
      in CONTAINER_TAGS -> element.blocks()
      else -> null
    }
  }

  private fun Element.items(): List<List<RichBlock>> =
    children().filter { it.tagName() == "li" }.map { it.blocks() }

  private fun Element.startFrom(): Int = attr("start").toIntOrNull() ?: 1

  /** Drains a run of loose inlines into a paragraph, or into nothing if it was only spacing. */
  private fun MutableList<RichInline>.takeParagraph(): List<RichBlock> {
    val run = trimEdges()
    clear()
    return if (run.isEmpty()) emptyList() else listOf(RichBlock.Paragraph(run))
  }

  private val CONTAINER_TAGS =
    setOf("div", "span", "section", "article", "figure", "figcaption", "main", "center", "details")
}
