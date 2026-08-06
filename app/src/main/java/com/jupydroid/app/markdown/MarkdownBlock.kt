package com.jupydroid.app.markdown

/**
 * What a markdown cell is made of, as far as drawing it is concerned.
 *
 * Blocks hold blocks - a quote contains paragraphs, a list item contains anything - so the
 * renderer is recursive rather than a loop over styled runs.
 *
 * Deliberately free of Compose: leaf blocks carry an inline *model* and the `AnnotatedString` is
 * built when it is drawn. That is what keeps this whole layer testable on the JVM, and it is the
 * shape JetBrains Jewel arrived at for the same problem
 * (`platform/jewel/markdown/core/.../MarkdownBlock.kt`).
 */
sealed interface MarkdownBlock {
  data class Heading(val level: Int, val inlines: List<MarkdownInline>) : MarkdownBlock

  data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock

  /** [language] is null for an indented block, which has no way to say one. */
  data class CodeBlock(val code: String, val language: String?) : MarkdownBlock

  data class BlockQuote(val children: List<MarkdownBlock>) : MarkdownBlock

  /** Each item is its own list of blocks, because an item may hold a paragraph and a nested list. */
  data class ListBlock(
    val ordered: Boolean,
    val startFrom: Int,
    val items: List<List<MarkdownBlock>>,
  ) : MarkdownBlock

  data object ThematicBreak : MarkdownBlock
}

/** The styled runs inside a block. Nested, so `**bold with *italic* inside**` survives. */
sealed interface MarkdownInline {
  data class Text(val text: String) : MarkdownInline

  data class Emphasis(val children: List<MarkdownInline>) : MarkdownInline

  data class StrongEmphasis(val children: List<MarkdownInline>) : MarkdownInline

  data class Code(val code: String) : MarkdownInline

  data class Link(val destination: String, val children: List<MarkdownInline>) : MarkdownInline

  /** A wrapped line inside one paragraph; renders as a space. */
  data object SoftLineBreak : MarkdownInline

  /** Two trailing spaces or a backslash; renders as a newline. */
  data object HardLineBreak : MarkdownInline
}

/** The text with every mark dropped - what a heading would read as, and what tests assert on. */
fun List<MarkdownInline>.plainText(): String = joinToString("") { inline ->
  when (inline) {
    is MarkdownInline.Text -> inline.text
    is MarkdownInline.Code -> inline.code
    is MarkdownInline.Emphasis -> inline.children.plainText()
    is MarkdownInline.StrongEmphasis -> inline.children.plainText()
    is MarkdownInline.Link -> inline.children.plainText()
    MarkdownInline.SoftLineBreak -> " "
    MarkdownInline.HardLineBreak -> "\n"
  }
}
