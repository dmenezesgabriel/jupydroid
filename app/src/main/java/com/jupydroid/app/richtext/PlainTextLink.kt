package com.jupydroid.app.richtext

import com.jupydroid.app.domain.model.SPACE_WORKSPACE_PATH

/** A web address or a path found in plain text: the text from [start] until [end], and where a tap on it goes. */
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

/**
 * JupyterLab's `posixPathRegex` and `lineColumnRegex` (jupyterlab@7f0e690
 * packages/rendermime/src/renderers.ts): an optional `~` or `.`, then `/` segments, then `:N`,
 * `", line N` or neither, then `:C` or not - so a link covers the line a frame names, as Lab's label
 * does. `\w` is spelled out: Lab's pattern has no `u` flag, so its `\w` is ASCII, where ART's ICU
 * engine would read `\w` as Unicode.
 */
private val PATH_LINK_PATTERN = Regex("((?:~|\\.)?(?:/[A-Za-z0-9_.-]*)+)(?:(?::|\", line )\\d+)?(?::\\d+)?")

/**
 * Every web address in this text, and with [withPaths] every path into the workspace too, in order.
 *
 * Paths are looked for between the web addresses, as JupyterLab's `autolink` runs its `PathLinker`
 * only on what its `WebLinker` left (jupyterlab@7f0e690 packages/rendermime/src/renderers.ts), so
 * an address's own path is never a second link. A path's destination is the workspace path as the
 * kernel sees it, `$SPACE_WORKSPACE_PATH/...`, whatever form it was printed in; a path outside the
 * workspace, or relative to a working directory an output does not know, stays text. A path draws
 * as a link whether or not its file is there, where Lab's `handlePathAnchor` asks the server's
 * `resolvePath` first: the tap checks it instead, and says when nothing is there
 * (`com.jupydroid.app.ui.editor.followWorkspaceLink`).
 *
 * ```
 * "File ~/mod.py:1, in f()".plainTextLinks(withPaths = true) // [PlainTextLink(5, 15, "/home/jupydroid/mod.py")]
 * ```
 */
fun String.plainTextLinks(withPaths: Boolean): List<PlainTextLink> {
  val webLinks = plainTextWebLinks()
  if (!withPaths) return webLinks
  val pathLinks = plainTextPathLinks().filter { path -> webLinks.none { it.start < path.end && path.start < it.end } }
  return (webLinks + pathLinks).sortedBy { it.start }
}

private fun String.plainTextPathLinks(): List<PlainTextLink> = PATH_LINK_PATTERN.findAll(this)
  .mapNotNull { match ->
    val path = match.groupValues[1].spaceWorkspaceLinkPath() ?: return@mapNotNull null
    PlainTextLink(match.range.first, match.range.last + 1, "$SPACE_WORKSPACE_PATH/$path")
  }
  .toList()
