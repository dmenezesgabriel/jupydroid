package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.jupydroid.app.SidePanel

/**
 * What each side panel is called and drawn as, for **both** controls that offer it.
 *
 * One home rather than two copies: the activity bar is a `NavigationRail` beside two panes
 * (`SidePanelRail`) and a `NavigationBar` below one (`SidePanelBar`), and VS Code's own bar is the
 * same bar in either place - `ActivityBarPosition.DEFAULT | TOP | BOTTOM`
 * (`vscode/…/services/layout/browser/layoutService.ts:75-80`), one set of containers throughout.
 * A panel that is named or drawn differently by width would be a second activity bar.
 */

/**
 * What the activity bar calls each panel. Each reference's own caption for the panel it owns -
 * `type: 'File Browser'` (`packages/filebrowser-extension/src/index.ts:676`),
 * `title: "Search"` (`vscode/…/search/browser/search.contribution.ts:55`) and
 * `type: 'Table of Contents'` (`packages/toc-extension/src/index.ts:309`) - in this app's sentence
 * case, and "Files" because that is already the name its own screen carries.
 */
internal val SidePanel.label: String
  get() = when (this) {
    SidePanel.FILES -> "Files"
    SidePanel.SEARCH -> "Search"
    SidePanel.TABLE_OF_CONTENTS -> "Table of contents"
  }

/** Material's nearest equivalents of `folder.svg`, `search.svg` and `toc.svg` in the icon registry. */
internal val SidePanel.icon: ImageVector
  get() = when (this) {
    SidePanel.FILES -> Icons.Filled.Folder
    // **Not** AutoMirrored, unlike the two below, and that is Material's own rule rather than an
    // omission: the magnifier is on its list of icons that keep their direction in a right-to-left
    // locale, which is why the icon set ships no mirrored variant of it to import.
    SidePanel.SEARCH -> Icons.Filled.Search
    // AutoMirrored, for the reason the explorer's own file icon is: the glyph's ragged edge
    // flips in a right-to-left locale, and the plain `Icons.Filled.Toc` is deprecated for it.
    SidePanel.TABLE_OF_CONTENTS -> Icons.AutoMirrored.Filled.Toc
  }
