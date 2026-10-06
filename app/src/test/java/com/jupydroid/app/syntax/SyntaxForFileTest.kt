package com.jupydroid.app.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which highlighter a file gets, chosen by its name.
 *
 * JupyterLab keeps the same table and keys it the same way - a registry of file types, each with
 * the `extensions` it claims (`packages/docregistry/src/registry.ts:1356-1508`): `.txt` text,
 * `.ipynb` notebook, `.md` markdown, `.py` python, `.json` json, `.csv` csv, `.yaml`/`.yml` yaml.
 * Its editor then loads the matching CodeMirror language; ours picks a [SyntaxHighlighter].
 *
 * We do not claim a type we cannot colour. A `.csv` gets plain text rather than a wrong guess,
 * because the alternative is inventing highlighting the reference gets from a real grammar.
 */
class SyntaxForFileTest {
  @Test
  fun pythonIsHighlightedAsPython() {
    assertTrue(syntaxForFile("train.py") is PythonSyntax)
  }

  @Test
  fun markdownIsHighlightedAsMarkdown() {
    assertTrue(syntaxForFile("README.md") is MarkdownSyntax)
  }

  @Test
  fun jsonIsHighlightedAsJson() {
    assertTrue(syntaxForFile("config.json") is JsonSyntax)
  }

  /**
   * JupyterLab's registry claims `sh`, `ksh` and `bash` for its Shell language
   * (`packages/codemirror/src/language.ts`).
   */
  @Test
  fun shellScriptsAreHighlightedAsShell() {
    listOf("setup.sh", "build.bash", "old.ksh").forEach { assertTrue(it, syntaxForFile(it) is ShellSyntax) }
  }

  /** The extension decides, not the case of it - `.PY` is a Python file to anyone reading it. */
  @Test
  fun theExtensionIsMatchedWithoutRegardToCase() {
    assertTrue(syntaxForFile("TRAIN.PY") is PythonSyntax)
  }

  /**
   * A type we have no grammar for reads as plain text rather than as a guess.
   *
   * The reference has a `csv` file type, but what it *does* with it is a data grid, not syntax
   * colouring - so colouring commas here would be our invention and not its behaviour.
   */
  @Test
  fun aTypeWithNoGrammarIsPlainText() {
    assertEquals(emptyList<SyntaxSpan>(), syntaxForFile("data.csv").spansOf("a,b\n1,2"))
    assertEquals(emptyList<SyntaxSpan>(), syntaxForFile("notes.txt").spansOf("hello"))
    assertEquals(emptyList<SyntaxSpan>(), syntaxForFile("Makefile").spansOf("all:"))
  }
}

/**
 * JSON, which is the one grammar this adds.
 *
 * Small on purpose: JSON has four things worth colouring and no ambiguity about any of them.
 * `true`, `false` and `null` are keywords because that is what they are to a reader - the
 * reference's CodeMirror mode calls them `atom` and colours them alongside its keywords.
 */
class JsonSyntaxTest {
  private val json = JsonSyntax()

  @Test
  fun aStringIsAString() {
    assertEquals(listOf(SyntaxSpan(0, 4, SyntaxKind.STRING)), json.spansOf(""" "ab" """.trim()))
  }

  @Test
  fun keysAndValuesAreBothStrings() {
    val spans = json.spansOf("""{"k": "v"}""")
    assertEquals(listOf(SyntaxSpan(1, 4, SyntaxKind.STRING), SyntaxSpan(6, 9, SyntaxKind.STRING)), spans)
  }

  /** `"a\"b"` is one string of six characters, not two strings with a stray `b` between them. */
  @Test
  fun anEscapedQuoteDoesNotEndTheString() {
    assertEquals(listOf(SyntaxSpan(0, 6, SyntaxKind.STRING)), json.spansOf("\"a\\\"b\""))
  }

  @Test
  fun numbersIncludeTheirSignAndExponent() {
    assertEquals(listOf(SyntaxSpan(0, 8, SyntaxKind.NUMBER)), json.spansOf("-1.5e-03"))
  }

  @Test
  fun theThreeLiteralsAreKeywords() {
    val spans = json.spansOf("[true, false, null]")
    assertEquals(
      listOf(
        SyntaxSpan(1, 5, SyntaxKind.KEYWORD),
        SyntaxSpan(7, 12, SyntaxKind.KEYWORD),
        SyntaxSpan(14, 18, SyntaxKind.KEYWORD),
      ),
      spans,
    )
  }

  /** Punctuation and whitespace are left alone, so a malformed file still renders. */
  @Test
  fun structureIsNotColouredAndMalformedInputStillReturns() {
    assertEquals(emptyList<SyntaxSpan>(), json.spansOf("{[,:}]"))
    assertEquals(listOf(SyntaxSpan(0, 3, SyntaxKind.STRING)), json.spansOf("\"ab"))
  }
}
