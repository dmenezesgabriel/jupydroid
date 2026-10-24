package com.jupydroid.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.ui.editor.RichText
import com.jupydroid.extension.api.MarkdownRenderer

/**
 * The markdown renderer an extension is handed: [MarkdownDocument] into [RichText], the path a
 * markdown cell is drawn by (`app/src/main/java/com/jupydroid/app/ui/editor/MarkdownCell.kt`).
 *
 * ```
 * SidePanelHost(browsedDirectory, server, spaceId = "base", markdown = RichTextMarkdownRenderer)
 * ```
 */
object RichTextMarkdownRenderer : MarkdownRenderer {
  @Composable
  override fun Draw(source: String, modifier: Modifier, codeBlockActions: @Composable (code: String) -> Unit) {
    // A streamed answer recomposes on every frame that grows it; one parse per source, not per frame.
    val blocks = remember(source) { MarkdownDocument.parse(source) }
    RichText(blocks, modifier, codeBlockActions)
  }
}
