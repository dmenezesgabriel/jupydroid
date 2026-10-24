package com.jupydroid.extension.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The host's markdown renderer, the one a markdown cell is drawn with, handed to an extension so
 * what it shows as markdown reads as the notebook does.
 *
 * The host's rather than the extension's own, as jupyter-chat's plugin `requires` the host's
 * `IRenderMimeRegistry` (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts`) and
 * a VS Code chat participant streams markdown the workbench draws (vscode@3f3380c
 * `src/vscode-dts/vscode.d.ts`, `ChatResponseStream.markdown`).
 *
 * ```
 * host.markdown.Draw("## Result\n\n```\nx = 1\n```", Modifier.padding(8.dp)) { code -> Text(code) }
 * ```
 */
fun interface MarkdownRenderer {
  /**
   * Draws [source], a CommonMark document with GitHub's tables, strikethrough and autolinks.
   *
   * @param codeBlockActions the extension's own, drawn under every code block, a nested one too, and
   *   handed its code as shown: jupyter-chat puts its toolbar after every `<pre>` the rendermime drew
   *   (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/messages/message-renderer.tsx`).
   */
  @Composable
  fun Draw(source: String, modifier: Modifier, codeBlockActions: @Composable (code: String) -> Unit)
}
