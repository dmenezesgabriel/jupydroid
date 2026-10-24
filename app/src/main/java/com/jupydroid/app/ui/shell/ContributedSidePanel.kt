package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SidePanelHost

/**
 * A panel an extension contributed, in the side bar's chrome: the title bar, the surface and the
 * inset are the shell's, and only the body is the extension's (ADR-0104).
 *
 * The chrome stays here because it is drawn from this app's palette and spacing, which an extension
 * cannot import - so every contributed panel wears the same chrome as the shell's own, without each
 * extension restating it.
 *
 * ```
 * ContributedSidePanel(contribution, SidePanelHost(browsedDirectory = "demo", server = endpoint, spaceId = "base"))
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributedSidePanel(contribution: SidePanelContribution, host: SidePanelHost, modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = chromePalette.sideBarBackground,
    topBar = { TopAppBar(colors = sideBarTopAppBarColors(), title = { Text(contribution.label) }) },
  ) { padding ->
    Box(modifier = Modifier.padding(padding).padding(Spacing.md)) {
      contribution.content(host)
    }
  }
}
