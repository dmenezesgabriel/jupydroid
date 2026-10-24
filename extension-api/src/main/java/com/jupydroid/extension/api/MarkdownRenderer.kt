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
 * host.markdown.Draw("## Result\n\n- one", Modifier.padding(8.dp))
 * ```
 */
fun interface MarkdownRenderer {
  /** Draws [source], a CommonMark document with GitHub's tables, strikethrough and autolinks. */
  @Composable
  fun Draw(source: String, modifier: Modifier)
}
