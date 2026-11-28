package com.jupydroid.app.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IPython cell highlighting: the `%`/`%%` sigil, the magic's name and what a `%%` cell's body is
 * highlighted as. The roles are `ipython_pygments_lexers`' - sigil `Operator`, name `Keyword`,
 * args `Text` - and the placement rules are the kernel's own
 * (`usr/lib/python3.12/site-packages/IPython/core/inputtransformer2.py`), because those are the
 * rules that decide what actually executes.
 */
class IPythonSyntaxTest {
  private val syntax: SyntaxHighlighter = IPythonSyntax()

  @Test
  fun aLineMagicPaintsTheSigilTheNameAndLeavesTheArgsPlain() {
    val source = "%matplotlib inline"

    assertEquals(
      listOf(
        SyntaxSpan(0, 1, SyntaxKind.MAGIC),
        SyntaxSpan(1, 11, SyntaxKind.KEYWORD),
      ),
      syntax.spansOf(source),
    )
  }

  /**
   * A magic's arguments are `Text` in the reference, not Python - a string in the args must not
   * pick up the STRING colour, so the plain answer is suppressed from the sigil to the line's end.
   */
  @Test
  fun argsAfterTheNameStayPlainEvenWhenTheyLookLikePython() {
    val source = "%pip install \"six\""

    val spans = syntax.spansOf(source)

    assertEquals(SyntaxKind.PLAIN, kindAt(spans, source.indexOf("six")))
    assertEquals(SyntaxKind.PLAIN, kindAt(spans, source.indexOf("install")))
  }

  /**
   * `EscapedCommand` skips INDENT and DEDENT tokens, so an indented `%` is still a line magic -
   * a `%ls` inside a function body is real, and the kernel runs it there.
   */
  @Test
  fun anIndentedPercentIsStillALineMagic() {
    val source = "def f():\n    %ls"
    val sigil = source.indexOf('%')

    val spans = syntax.spansOf(source)

    assertEquals(SyntaxKind.MAGIC, kindAt(spans, sigil))
    assertEquals(SyntaxKind.KEYWORD, kindAt(spans, sigil + 1))
    assertEquals(SyntaxKind.KEYWORD, kindAt(spans, 0)) // `def` still paints - the cell stays Python
  }

  /**
   * `MagicAssign` transforms `x = %foo`: a bare `=` followed by `%` names an assignment whose
   * value is the magic's output. `==` is a comparison, `+=` another op - only `=` counts.
   */
  @Test
  fun anAssignmentFromAMagicPaintsTheSigilAndName() {
    val source = "x = %ls"

    assertEquals(
      listOf(
        SyntaxSpan(4, 5, SyntaxKind.MAGIC),
        SyntaxSpan(5, 7, SyntaxKind.KEYWORD),
      ),
      syntax.spansOf(source),
    )
  }

  @Test
  fun aComparisonOrAugmentedAssignIsNotAMagicAssign() {
    listOf("x == %ls", "x += %ls").forEach { source ->
      assertTrue("$source must not paint a sigil", syntax.spansOf(source).none { it.kind == SyntaxKind.MAGIC })
    }
  }

  /**
   * The kernel only transforms a `%` at logical-line level - inside parens the `%` is an error,
   * not a magic, so neither `f(x = %ls)` nor a `%` on its own continuation line may light.
   */
  @Test
  fun aPercentInsideParensIsNotAMagic() {
    listOf("f(x = %ls)", "x = (\n%ls\n)").forEach { source ->
      assertTrue("$source must not paint a sigil", syntax.spansOf(source).none { it.kind == SyntaxKind.MAGIC })
    }
  }

  @Test
  fun aCellMagicPaintsTheDoubleSigilTheNameAndTheBodyInItsLanguage() {
    val source = "%%bash\necho hi"

    assertEquals(
      listOf(
        SyntaxSpan(0, 2, SyntaxKind.MAGIC),
        SyntaxSpan(2, 6, SyntaxKind.KEYWORD),
        SyntaxSpan(7, 11, SyntaxKind.FUNCTION),
      ),
      syntax.spansOf(source),
    )
  }

  /**
   * `cell_magic` fires on `lines[0].startswith("%%")` - first line, column zero. An indented or
   * mid-cell `%%` is a `UsageError` at runtime, and the highlighter must not dress it as one.
   */
  @Test
  fun aCellMagicOnlyCountsOnTheFirstLineAtColumnZero() {
    listOf("x = 1\n%%bash\necho hi", "  %%bash\necho hi").forEach { source ->
      assertTrue("$source must not paint a sigil", syntax.spansOf(source).none { it.kind == SyntaxKind.MAGIC })
    }
  }

  /** `a % b` is modulo, not a magic - pygments' unanchored rule would light it; the kernel's does not. */
  @Test
  fun moduloStaysPlain() {
    val source = "a % b"

    assertTrue(syntax.spansOf(source).none { it.kind == SyntaxKind.MAGIC })
  }

  /**
   * The scanner is line-based where `ipython_pygments_lexers` is stateful, so a `%` on a line
   * inside a triple-quoted string or a comment would false-light without the STRING/COMMENT mask.
   */
  @Test
  fun aPercentInsideAStringOrCommentIsNotAMagic() {
    listOf("s = \"\"\"docs\n%ls\nmore\"\"\"", "# %ls\nx = 1", "x = 'a % b'").forEach { source ->
      assertTrue("$source must not paint a sigil", syntax.spansOf(source).none { it.kind == SyntaxKind.MAGIC })
    }
  }

  /**
   * A lone sigil still draws - the kernel errors on it, and the sigil is how an editor reads
   * "this was meant as a magic". A half-typed name is a name as far as it goes.
   */
  @Test
  fun loneAndHalfTypedSigilsStillDraw() {
    assertEquals(listOf(SyntaxSpan(0, 1, SyntaxKind.MAGIC)), syntax.spansOf("%"))
    assertEquals(listOf(SyntaxSpan(0, 2, SyntaxKind.MAGIC)), syntax.spansOf("%%"))
    assertEquals(
      listOf(SyntaxSpan(0, 1, SyntaxKind.MAGIC), SyntaxSpan(1, 4, SyntaxKind.KEYWORD)),
      syntax.spansOf("%mat"),
    )
  }

  /**
   * The body table: `%%bash`-family cells are shell, `%%markdown` is markdown, the
   * `%%time`/`%%capture`/`%%python*` family stays Python, `%%!` is a shell cell like the
   * reference's own row, and a name with no language we can paint keeps the body plain - a
   * language we cannot honour claims no colouring.
   */
  @Test
  fun aCellMagicBodyTakesTheLanguageItsNameRuns() {
    assertEquals(SyntaxKind.FUNCTION, kindAt(syntax.spansOf("%%script bash\nls"), "%%script bash\nls".indexOf("ls")))
    assertEquals(SyntaxKind.NUMBER, kindAt(syntax.spansOf("%%time\nx = 1"), "%%time\nx = 1".indexOf("1")))
    assertEquals(SyntaxKind.HEADING, kindAt(syntax.spansOf("%%markdown\n# Hi"), "%%markdown\n# Hi".indexOf("Hi")))
    assertEquals(SyntaxKind.FUNCTION, kindAt(syntax.spansOf("%%!\nls"), "%%!\nls".indexOf("ls")))
    assertTrue(syntax.spansOf("%%ruby\nputs hi").none { it.start > "%%ruby".length })
  }

  /** Ordinary code around a magic line keeps its Python colouring - the base pass survives. */
  @Test
  fun theRestOfTheCellStaysPython() {
    val source = "x = 1\n%ls\ny = \"a\""

    val spans = syntax.spansOf(source)

    assertEquals(SyntaxKind.NUMBER, kindAt(spans, source.indexOf("1")))
    assertEquals(SyntaxKind.STRING, kindAt(spans, source.indexOf("a")))
  }

  /** Cells are edited constantly, so half-written code is the normal case, not the edge. */
  @Test
  fun spansOfToleratesIncompleteCodeWithoutThrowing() {
    listOf("%", "%%", "%x =", "x = %", "%%bash", "%%bash\n", "%\n%", "x = (\n").forEach { source ->
      syntax.spansOf(source) // must not throw
    }
  }

  private fun kindAt(spans: List<SyntaxSpan>, offset: Int): SyntaxKind =
    spans.firstOrNull { offset >= it.start && offset < it.end }?.kind ?: SyntaxKind.PLAIN
}
