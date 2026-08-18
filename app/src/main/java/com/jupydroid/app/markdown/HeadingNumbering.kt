package com.jupydroid.app.markdown

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline

/**
 * The same blocks with [prefix] written onto the heading that heads them, or exactly the blocks
 * that came in when there is nothing to number.
 *
 * This is the half of `toc:display-numbering` its own label names - *"Show heading number in the
 * document"*: the reference puts the prefix into the **rendered heading itself**
 * (`packages/notebook/src/toc.ts:733-748`, `addPrefix` over `heading.cellRef.node`) and not only
 * into the panel, so numbering that showed in the list alone would be a control that lies.
 *
 * It goes on the heading [MarkdownDocument.heading] picked - the lowest-numbered one, which is the
 * one the table of contents has an entry for - rather than on the first one written, since those
 * are not always the same cell's heading.
 *
 * The prefix rides as an ordinary text inline, so it takes the heading's own style and is what a
 * screen reader reads first. The reference wraps it in a `span` for the same reason: it is part of
 * the heading now.
 *
 * ```
 * MarkdownDocument.parse("## Results").withHeadingNumbered("1.2. ")  // "1.2. Results"
 * ```
 */
fun List<RichBlock>.withHeadingNumbered(prefix: String): List<RichBlock> {
  val numbered = if (prefix.isEmpty()) null else MarkdownDocument.heading(this)
  return map { block ->
    if (block !== numbered) block else block.copy(inlines = listOf(RichInline.Text(prefix)) + block.inlines)
  }
}
