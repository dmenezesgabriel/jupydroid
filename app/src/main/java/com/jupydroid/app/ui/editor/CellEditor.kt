package com.jupydroid.app.ui.editor

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * One cell's CodeMirror editor, hosted in a WebView.
 *
 * The page is `assets/editor/index.html`, built by `tools/editor/build.sh`. Everything about
 * the protocol lives in [EditorBridge]; this is the plumbing that carries it, which is why
 * there is so little logic here to get wrong.
 *
 * Height is fixed for now - the page already reports its own, and Compose acting on that is
 * Step 5.4.
 */
@Composable
fun CellEditor(
  source: String,
  theme: EditorTheme,
  onSourceEdited: (String) -> Unit,
  onRunRequested: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Survives recomposition so a cell being redrawn does not reload its editor.
  val page = remember { mutableStateOf<EditorPageHandle?>(null) }

  AndroidView(
    modifier = modifier,
    factory = { context ->
      editorWebView(context, theme, onSourceEdited, onRunRequested) { page.value = it }
    },
    // Queued until the page reports ready; the page ignores a source equal to what it already
    // holds, so echoing our own edit back cannot loop.
    update = { page.value?.showSource(source) },
  )
}

@SuppressLint("SetJavaScriptEnabled")
private fun editorWebView(
  context: android.content.Context,
  theme: EditorTheme,
  onSourceEdited: (String) -> Unit,
  onRunRequested: () -> Unit,
  onHandleCreated: (EditorPageHandle) -> Unit,
): WebView = WebView(context).apply {
  val page = EditorPageHandle { js -> evaluateJavascript(js, null) }
  page.applyTheme(theme.tokens())
  onHandleCreated(page)
  settings.javaScriptEnabled = true // the editor is a JS bundle; without this there is no editor
  // Nothing is loaded over the network: the page ships in the APK.
  settings.blockNetworkLoads = true
  setBackgroundColor(theme.background.toArgb())

  // Safe because the page is our own asset and the interface exposes exactly one method that
  // takes a string. The danger with addJavascriptInterface is remote content, which
  // blockNetworkLoads plus a file:///android_asset URL rules out.
  addJavascriptInterface(
    EditorPageChannel { message -> post { page.handle(message, onSourceEdited, onRunRequested) } },
    "JupydroidBridge",
  )
  loadUrl(EDITOR_PAGE)
}

private fun EditorPageHandle.handle(
  message: EditorMessage,
  onSourceEdited: (String) -> Unit,
  onRunRequested: () -> Unit,
) {
  when (message) {
    EditorMessage.Ready -> onReady()
    is EditorMessage.SourceEdited -> onSourceEdited(message.source)
    EditorMessage.RunRequested -> onRunRequested()
    // Height is Step 5.4; completion is 5.6 and needs KernelGateway.complete.
    is EditorMessage.HeightChanged, is EditorMessage.CompletionRequested -> Unit
  }
}

/**
 * The `window.JupydroidBridge` the page posts to.
 *
 * Its one method runs on the WebView's JavaScript thread, so the callback hops back to the UI
 * thread before touching Compose state - a class rather than a lambda because
 * `@JavascriptInterface` has to be on a real method for the annotation to survive.
 */
private class EditorPageChannel(private val onMessage: (EditorMessage) -> Unit) {
  @JavascriptInterface
  fun postMessage(rawMessage: String) {
    EditorBridge.parse(rawMessage)?.let(onMessage)
  }
}

private const val EDITOR_PAGE = "file:///android_asset/editor/index.html"
