package com.jupydroid.app.syntax

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Shell highlighting, after the mode JupyterLab loads for `text/x-sh`: CodeMirror's legacy `shell`
 * (`@codemirror/legacy-modes@6.5.2 mode/shell.js`). Each case is one of that mode's token rules,
 * asserted as the exact spans it yields, so a rule that grows or shrinks a span fails here.
 */
class ShellSyntaxTest {
  private val syntax: SyntaxHighlighter = ShellSyntax()

  @Test
  fun aCommonCommandIsColouredAsAFunction() {
    assertEquals(listOf(SyntaxSpan(0, 4, SyntaxKind.FUNCTION)), syntax.spansOf("echo hi"))
  }

  @Test
  fun keywordsAndTheTwoAtomsAreKeywordsAndAStatusIsANumber() {
    val source = "if true; then exit 3; fi"

    assertEquals(
      listOf(
        SyntaxSpan(0, 2, SyntaxKind.KEYWORD),
        SyntaxSpan(3, 7, SyntaxKind.KEYWORD),
        SyntaxSpan(9, 13, SyntaxKind.KEYWORD),
        SyntaxSpan(14, 18, SyntaxKind.KEYWORD),
        SyntaxSpan(19, 20, SyntaxKind.NUMBER),
        SyntaxSpan(22, 24, SyntaxKind.KEYWORD),
      ),
      syntax.spansOf(source),
    )
  }

  /** The reference's list closes `esac` and never opens `case`; a reader would not split them. */
  @Test
  fun caseIsAKeywordBesideItsEsac() {
    assertEquals(SyntaxKind.KEYWORD, kindAt(syntax.spansOf("case \$1 in esac"), 0))
  }

  @Test
  fun aCommentRunsToTheEndOfItsLineOnly() {
    val source = "#!/bin/sh\necho # done"

    assertEquals(
      listOf(
        SyntaxSpan(0, 9, SyntaxKind.COMMENT),
        SyntaxSpan(10, 14, SyntaxKind.FUNCTION),
        SyntaxSpan(15, 21, SyntaxKind.COMMENT),
      ),
      syntax.spansOf(source),
    )
  }

  /** `#` starts a comment only where a word could start - bash reads `a#b` as one word. */
  @Test
  fun aHashInsideAWordIsNotAComment() {
    assertEquals(listOf(SyntaxSpan(0, 4, SyntaxKind.FUNCTION)), syntax.spansOf("echo a#b"))
  }

  @Test
  fun anAssignmentNamesAVariable() {
    assertEquals(
      listOf(
        SyntaxSpan(0, 6, SyntaxKind.KEYWORD),
        SyntaxSpan(7, 8, SyntaxKind.VARIABLE),
        SyntaxSpan(9, 10, SyntaxKind.NUMBER),
      ),
      syntax.spansOf("export x=1"),
    )
  }

  @Test
  fun everyFormOfExpansionIsAVariable() {
    val source = "\${PATH}:\$1:\$?:\$HOME_DIR"

    assertEquals(
      listOf(
        SyntaxSpan(0, 7, SyntaxKind.VARIABLE),
        SyntaxSpan(8, 10, SyntaxKind.VARIABLE),
        SyntaxSpan(11, 13, SyntaxKind.VARIABLE),
        SyntaxSpan(14, 23, SyntaxKind.VARIABLE),
      ),
      syntax.spansOf(source),
    )
  }

  @Test
  fun aSingleQuotedStringExpandsNothing() {
    assertEquals(
      listOf(SyntaxSpan(0, 4, SyntaxKind.FUNCTION), SyntaxSpan(5, 11, SyntaxKind.STRING)),
      syntax.spansOf("echo 'a \$b'"),
    )
  }

  @Test
  fun aDoubleQuotedStringIsSplitAroundItsExpansion() {
    val source = "\"a \$HOME b\""

    assertEquals(
      listOf(
        SyntaxSpan(0, 3, SyntaxKind.STRING),
        SyntaxSpan(3, 8, SyntaxKind.VARIABLE),
        SyntaxSpan(8, 11, SyntaxKind.STRING),
      ),
      syntax.spansOf(source),
    )
  }

  /** The reference styles a backquote `quote`; it is a string of a kind, so it takes that colour. */
  @Test
  fun aBackquotedCommandIsAString() {
    assertEquals(listOf(SyntaxSpan(0, 6, SyntaxKind.STRING)), syntax.spansOf("`date`"))
  }

  /** Half-written is the normal case in an editor: the string runs to the end, and nothing throws. */
  @Test
  fun anUnterminatedStringRunsToTheEnd() {
    assertEquals(listOf(SyntaxSpan(0, 4, SyntaxKind.STRING)), syntax.spansOf("\"abc"))
    assertEquals(listOf(SyntaxSpan(0, 1, SyntaxKind.VARIABLE)), syntax.spansOf("\$"))
    assertEquals(listOf(SyntaxSpan(0, 3, SyntaxKind.VARIABLE)), syntax.spansOf("\${a"))
  }

  @Test
  fun anEscapedDollarIsNotAnExpansion() {
    assertEquals(emptyList<SyntaxSpan>(), syntax.spansOf("\\\$HOME"))
  }

  /** Only a word of digits alone is a number; a name that starts with one is a name. */
  @Test
  fun aWordThatStartsWithDigitsIsNotANumber() {
    assertEquals(emptyList<SyntaxSpan>(), syntax.spansOf("3x my-tool2"))
  }

  @Test
  fun nothingYieldsNothing() {
    assertEquals(emptyList<SyntaxSpan>(), syntax.spansOf(""))
  }

  private fun kindAt(spans: List<SyntaxSpan>, offset: Int): SyntaxKind =
    spans.firstOrNull { offset >= it.start && offset < it.end }?.kind ?: SyntaxKind.PLAIN
}
