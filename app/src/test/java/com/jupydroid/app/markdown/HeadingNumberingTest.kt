package com.jupydroid.app.markdown

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.plainText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The other half of `toc:display-numbering`, and the half its own label names: *"Show heading
 * number in the **document**"*. The reference injects the prefix into the rendered heading itself
 * (`packages/notebook/src/toc.ts:733-748`, `addPrefix` over `heading.cellRef.node`), not only into
 * the panel - so a number that appeared in the list alone would be a label that lies.
 */
class HeadingNumberingTest {
  @Test
  fun theNumberGoesOnTheHeadingTheTableOfContentsNamed() {
    val blocks = MarkdownDocument.parse("## Results\n\nsome prose").withHeadingNumbered("1.2. ")

    assertEquals("1.2. Results", (blocks[0] as RichBlock.Heading).inlines.plainText())
    assertEquals("some prose", (blocks[1] as RichBlock.Paragraph).inlines.plainText())
  }

  /**
   * The *lowest-numbered* heading, which is the one [MarkdownDocument.heading] picked and so the
   * one the table of contents has an entry for - not simply the first one in the cell.
   */
  @Test
  fun theNumberGoesOnTheHeadingThatHeadsTheCell() {
    val blocks = MarkdownDocument.parse("### Detail\n\n# Chapter").withHeadingNumbered("2. ")

    assertEquals("Detail", (blocks[0] as RichBlock.Heading).inlines.plainText())
    assertEquals("2. Chapter", (blocks[1] as RichBlock.Heading).inlines.plainText())
  }

  @Test
  fun anEmptyPrefixLeavesTheBlocksExactlyAsTheyWere() {
    val blocks = MarkdownDocument.parse("# Chapter")

    assertEquals(blocks, blocks.withHeadingNumbered(""))
  }

  @Test
  fun proseHasNoHeadingToNumber() {
    val blocks = MarkdownDocument.parse("just some prose")

    assertEquals(blocks, blocks.withHeadingNumbered("1. "))
  }
}
