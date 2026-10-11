package com.jupydroid.app.richtext

/** A web address found in plain text: the text from [start] until [end], and where a tap on it goes. */
data class PlainTextLink(val start: Int, val end: Int, val destination: String)

/**
 * JupyterLab's `webLinkRegex` with its scheme narrowed to what `isWebLinkDestination` follows: a link
 * that draws and does nothing reads as broken, so `ftp://` and `data:` stay text.
 *
 * The rest is theirs, as VS Code's (vscode@3f3380c6 extensions/notebook-renderers/src/linkify.ts) is
 * the same pattern: past the prefix, no whitespace, control code or `"`, and a last character outside
 * the punctuation a sentence ends an address with (jupyterlab@7f0e690
 * packages/rendermime/src/renderers.ts, `ILinker.webLinkRegex`). `<` and `>` join that set, which is
 * what `WebLinker.processPath` does by trimming a trailing one. The pattern runs on ART's ICU engine,
 * not the JVM's, so a device test taps a link too (`CellOutputsViewTest`).
 */
private val WEB_LINK_PATTERN = Regex(
  "(?:https?://|www\\.)[^\\s\\u0000-\\u0020\\u007f-\\u009f\"]{2,}" +
    "[^\\s\\u0000-\\u0020\\u007f-\\u009f\"'\\(\\)\\{\\}\\[\\],:;.!?<>]",
  RegexOption.IGNORE_CASE,
)

/**
 * Every web address in this text, in order.
 *
 * A `www.` address goes over `https://`, as `WebLinker.createAnchor` sends it
 * (jupyterlab@7f0e690 packages/rendermime/src/renderers.ts); what is drawn stays what was printed.
 *
 * ```
 * "see www.python.org.".plainTextWebLinks() // [PlainTextLink(4, 18, "https://www.python.org")]
 * ```
 */
fun String.plainTextWebLinks(): List<PlainTextLink> = WEB_LINK_PATTERN.findAll(this)
  .map { PlainTextLink(it.range.first, it.range.last + 1, it.value.asWebLinkDestination()) }
  .toList()

private fun String.asWebLinkDestination(): String = if (startsWith("www.", ignoreCase = true)) "https://$this" else this
