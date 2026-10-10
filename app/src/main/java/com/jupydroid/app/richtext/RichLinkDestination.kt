package com.jupydroid.app.richtext

import java.net.URLDecoder

/**
 * The schemes a rendered link may leave the app with: the three VS Code hands to its opener from a
 * notebook's webview (`vscode@3f3380c6
 * src/vs/workbench/contrib/notebook/browser/view/renderers/backLayerWebView.ts`, `clicked-link`).
 *
 * An allowlist because the destination is written by a notebook's author, not by its reader: an
 * `intent:` or `file:` destination names another app's entry point, and `javascript:` names script.
 */
private val WEB_LINK_SCHEMES = setOf("http", "https", "mailto")

/**
 * Whether a link's destination is a web or mail address, which is what may be handed to another
 * app. A path, a `#fragment` and every other scheme are not.
 *
 * ```
 * "https://jupyter.org".isWebLinkDestination() // true
 * "notes.md".isWebLinkDestination() // false
 * ```
 */
fun String.isWebLinkDestination(): Boolean =
  substringBefore(':', missingDelimiterValue = "").lowercase() in WEB_LINK_SCHEMES

/**
 * The workspace path a link's destination names, resolved against [directory] - the directory of
 * the document the link is drawn in, `""` for the root - or null when the destination is not a
 * workspace path.
 *
 * Local is `URLExt.isLocal`'s rule: no scheme and no leading slash, so an absolute path, a
 * protocol-relative `//host` and every scheme are not workspace paths; neither is a bare
 * `#fragment`, which names a place in this document rather than a file. What is local is resolved
 * the way `RenderMimeRegistry.UrlResolver` resolves it - against the document's directory, then
 * decoded for the contents API (jupyterlab@7f0e690 `packages/coreutils/src/url.ts`,
 * `packages/rendermime/src/registry.ts`). A `..` above the root resolves to null: nothing there is
 * addressable (`com.jupydroid.app.domain.port.WorkspaceRepository`).
 *
 * ```
 * "../data/notes.md".workspaceLinkPath("reports") // "data/notes.md"
 * "https://jupyter.org".workspaceLinkPath("reports") // null
 * ```
 */
fun String.workspaceLinkPath(directory: String): String? {
  if (!isLocalLinkDestination()) return null
  return decodedLinkPath()?.resolvedAgainst(directory)
}

/** `URLExt.isLocal`, less a bare `#fragment`: not empty, no scheme, no leading slash. */
private fun String.isLocalLinkDestination(): Boolean =
  isNotEmpty() && !startsWith('/') && !startsWith('#') && !substringBefore('/').contains(':')

/**
 * The path half of a destination, percent-decoded, or null when an escape is malformed - which
 * `UrlResolver.isMalformed` (jupyterlab@7f0e690) treats as not local. `+` is a literal in a path,
 * not the space it is in a form, so it is protected from [URLDecoder]. The encoding is named as a
 * string because the `Charset` overload is API 33 and `minSdk` is 32 (`app/build.gradle.kts`).
 */
private fun String.decodedLinkPath(): String? = runCatching {
  URLDecoder.decode(substringBefore('#').substringBefore('?').replace("+", "%2B"), "UTF-8")
}.getOrNull()

/** `PathExt.resolve` for a relative path: `.` and empty segments drop, `..` climbs, above the root is null. */
private fun String.resolvedAgainst(directory: String): String? {
  val segments = directory.split('/').filter { it.isNotEmpty() }.toMutableList()
  for (segment in split('/')) {
    when (segment) {
      "", "." -> Unit
      ".." -> segments.removeLastOrNull() ?: return null
      else -> segments += segment
    }
  }
  return segments.joinToString("/")
}
