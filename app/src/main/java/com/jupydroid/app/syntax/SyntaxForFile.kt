package com.jupydroid.app.syntax

/**
 * The highlighter a file's name asks for.
 *
 * JupyterLab keeps the same table: a registry of file types, each claiming the `extensions` it
 * owns (`packages/docregistry/src/registry.ts:1356-1508`), and its editor loads the CodeMirror
 * language that matches. This is the same lookup with the languages we actually have.
 *
 * **Plain text is the answer for anything else, deliberately.** The reference has a `csv` type,
 * but what it gives it is a data grid, not syntax colouring - so colouring commas would be our
 * invention rather than its behaviour. A file always opens; it is never refused for want of a
 * grammar.
 *
 * ```
 * syntaxForFile("train.py")    // PythonSyntax
 * syntaxForFile("data.csv")    // plain text, and it still opens
 * ```
 */
fun syntaxForFile(fileName: String): SyntaxHighlighter =
  when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
    "py" -> PythonSyntax()
    "md", "markdown" -> MarkdownSyntax()
    "json", "ipynb" -> JsonSyntax()
    else -> PlainText
  }

/**
 * No spans at all, which renders the text in the editor's ordinary foreground colour.
 *
 * Shared with [com.jupydroid.app.ui.editor.RawCellField], which is the other place this app has
 * text and no language: `RawCellModel` is the one cell model that sets no mimeType
 * (`packages/cells/src/model.ts:550-578`).
 */
val PlainText = SyntaxHighlighter { emptyList() }
