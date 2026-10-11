package com.jupydroid.app.richtext

import com.jupydroid.app.domain.model.SPACE_WORKSPACE_PATH
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

/**
 * The workspace path a path the kernel printed names - `~/mod.py` or `/home/jupydroid/mod.py` -
 * or null when it names somewhere outside the workspace.
 *
 * The kernel's `HOME` is the workspace ([SPACE_WORKSPACE_PATH]), which is what IPython's
 * `compress_user` shortens to `~`; JupyterLab's legacy `resolvePath` expands `~` and strips the
 * server's root the same way (jupyterlab@7f0e690 `packages/rendermime/src/registry.ts`). Resolved
 * from the root, not from a document: the kernel printed it, not the notebook's author.
 *
 * ```
 * "~/a/mod.py".spaceWorkspaceLinkPath() // "a/mod.py"
 * "/usr/lib/python3.12/json/__init__.py".spaceWorkspaceLinkPath() // null
 * ```
 */
fun String.spaceWorkspaceLinkPath(): String? {
  val underHome = when {
    startsWith("~/") -> drop(1)
    startsWith("$SPACE_WORKSPACE_PATH/") -> drop(SPACE_WORKSPACE_PATH.length)
    else -> return null
  }
  return underHome.resolvedAgainst("")
}

/** `URLExt.isLocal`, less a bare `#fragment`: not empty, no scheme, no leading slash. */
private fun String.isLocalLinkDestination(): Boolean =
  isNotEmpty() && !startsWith('/') && !startsWith('#') && !substringBefore('/').contains(':')

/** The path half of a destination, percent-decoded, or null when an escape is malformed. */
private fun String.decodedLinkPath(): String? = substringBefore('#').substringBefore('?').percentDecoded()

/**
 * The heading a bare `#fragment` names in the document the link is drawn in, percent-decoded, or
 * null when the destination is not a bare fragment - a path's fragment goes with its file - or
 * names nothing (`#` alone).
 *
 * JupyterLab's `handleAnchor` takes over only an `href` that *is* its `hash`
 * (jupyterlab@7f0e690 `packages/rendermime/src/renderers.ts`), and it compares the escaped `hash`
 * with an unescaped id; decoding here is what lets `#My%20Notes` find "My Notes".
 *
 * ```
 * "#Results".headingLinkFragment() // "Results"
 * "notes.md#intro".headingLinkFragment() // null
 * ```
 */
fun String.headingLinkFragment(): String? {
  if (!startsWith('#')) return null
  return drop(1).percentDecoded()?.takeIf { it.isNotEmpty() }
}

/**
 * Percent-decoded, or null when an escape is malformed - which `UrlResolver.isMalformed`
 * (jupyterlab@7f0e690) treats as not local. `+` is a literal in a path, not the space it is in a
 * form, so it is protected from [URLDecoder]. The encoding is named as a string because the
 * `Charset` overload is API 33 and `minSdk` is 32 (`app/build.gradle.kts`).
 */
private fun String.percentDecoded(): String? =
  runCatching { URLDecoder.decode(replace("+", "%2B"), "UTF-8") }.getOrNull()

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
