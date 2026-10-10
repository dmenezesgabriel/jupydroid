package com.jupydroid.app.richtext

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
