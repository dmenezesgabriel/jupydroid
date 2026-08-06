package com.jupydroid.app.richtext

/**
 * What a rendered document is made of, as far as drawing it is concerned.
 *
 * Shared rather than markdown's own, because HTML output arrives with the same vocabulary -
 * `MarkdownDocument` and `HtmlDocument` are two front ends onto one model and one renderer.
 *
 * Blocks hold blocks - a quote contains paragraphs, a list item contains anything - so the
 * renderer is recursive rather than a loop over styled runs.
 *
 * Deliberately free of Compose: leaf blocks carry an inline *model* and the `AnnotatedString` is
 * built when it is drawn. That is what keeps this whole layer testable on the JVM, and it is the
 * shape JetBrains Jewel arrived at for the same problem
 * (`platform/jewel/markdown/core/.../MarkdownBlock.kt`).
 */
sealed interface RichBlock {
  data class Heading(val level: Int, val inlines: List<RichInline>) : RichBlock

  data class Paragraph(val inlines: List<RichInline>) : RichBlock

  /** [language] is null for an indented block, which has no way to say one. */
  data class CodeBlock(val code: String, val language: String?) : RichBlock

  data class BlockQuote(val children: List<RichBlock>) : RichBlock

  /** Each item is its own list of blocks, because an item may hold a paragraph and a nested list. */
  data class ListBlock(
    val ordered: Boolean,
    val startFrom: Int,
    val items: List<List<RichBlock>>,
  ) : RichBlock

  data object ThematicBreak : RichBlock
}

/** The styled runs inside a block. Nested, so `**bold with *italic* inside**` survives. */
sealed interface RichInline {
  data class Text(val text: String) : RichInline

  data class Emphasis(val children: List<RichInline>) : RichInline

  data class StrongEmphasis(val children: List<RichInline>) : RichInline

  data class Code(val code: String) : RichInline

  data class Link(val destination: String, val children: List<RichInline>) : RichInline

  /** A wrapped line inside one paragraph; renders as a space. */
  data object SoftLineBreak : RichInline

  /** Two trailing spaces or a backslash; renders as a newline. */
  data object HardLineBreak : RichInline
}

/** The text with every mark dropped - what a heading would read as, and what tests assert on. */
fun List<RichInline>.plainText(): String = joinToString("") { inline ->
  when (inline) {
    is RichInline.Text -> inline.text
    is RichInline.Code -> inline.code
    is RichInline.Emphasis -> inline.children.plainText()
    is RichInline.StrongEmphasis -> inline.children.plainText()
    is RichInline.Link -> inline.children.plainText()
    RichInline.SoftLineBreak -> " "
    RichInline.HardLineBreak -> "\n"
  }
}
