package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.Spacing
import com.jupydroid.extension.api.DocumentContribution
import com.jupydroid.extension.api.SidePanelHost

/**
 * A document an extension contributed, framed as [ContributedSidePanel] frames a panel (ADR-0105):
 * the surface and the inset are the shell's, the body is the extension's. No title bar, because
 * the tab above it already names the file.
 *
 * ```
 * ContributedDocumentPane(contribution, "notes/a.chat", host)
 * ```
 */
@Composable
fun ContributedDocumentPane(
  contribution: DocumentContribution,
  path: String,
  host: SidePanelHost,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier.fillMaxSize()) {
    // The pane reaches the window's bottom edge, which a side panel at two panes never does: without
    // these the body's last row - a chat's field and chips - is drawn under the taskbar and the
    // keyboard, as `ConsoleScreen`'s input row would be.
    Box(
      modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(Spacing.md),
    ) {
      contribution.content(path, host)
    }
  }
}
