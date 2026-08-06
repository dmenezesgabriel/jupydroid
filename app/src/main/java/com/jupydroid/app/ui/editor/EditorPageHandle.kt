package com.jupydroid.app.ui.editor

/**
 * Holds back calls into the editor page until it exists.
 *
 * A WebView loads asynchronously, but Compose will try to push the cell's source on the very
 * first composition - `window.JupydroidEditor` is undefined then, and the injected call dies
 * with *Cannot read properties of undefined*, leaving a blank editor and nothing on screen to
 * explain it. The page's `ready` message is the signal to start talking.
 *
 * ```
 * val page = EditorPageHandle { js -> webView.evaluateJavascript(js, null) }
 * page.showSource("2 + 2")   // queued
 * page.onReady()             // sent, along with the theme
 * ```
 */
class EditorPageHandle(private val evaluate: (String) -> Unit) {
  private var ready = false
  private var latestSource: String? = null
  private var latestTheme: Map<String, String>? = null

  /** The most recent source wins: earlier ones were never seen and are not worth replaying. */
  fun showSource(source: String) {
    latestSource = source
    if (ready) evaluate(EditorBridge.setSource(source))
  }

  fun applyTheme(tokens: Map<String, String>) {
    latestTheme = tokens
    if (ready) evaluate(EditorBridge.setTheme(tokens))
  }

  fun onReady() {
    ready = true
    latestTheme?.let { evaluate(EditorBridge.setTheme(it)) }
    latestSource?.let { evaluate(EditorBridge.setSource(it)) }
  }
}
