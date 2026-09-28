package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.chromePalette

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
    // `jpTabLabel` - the tab's own name where the caption "Running Terminals and Kernels" is the
    // longer form for tooltips (`running-extension/src/index.ts:101-105`).
    SidePanel.RUNNING -> "Sessions and Tabs"
    SidePanel.SEARCH -> "Search"
    SidePanel.TABLE_OF_CONTENTS -> "Table of contents"
    SidePanel.DEBUGGER -> "Debugger"
  }

/** Material's nearest equivalents of `folder.svg`, `search.svg` and `toc.svg` in the icon registry. */
internal val SidePanel.icon: ImageVector
  get() = when (this) {
    SidePanel.FILES -> Icons.Filled.Folder
    // `ui-components:running` (`sidebar/running.svg`) is a circle holding a rounded square - the
    // stop glyph - and `StopCircle` is Material's own drawing of exactly that shape.
    SidePanel.RUNNING -> Icons.Filled.StopCircle
    // **Not** AutoMirrored, unlike the two below, and that is Material's own rule rather than an
    // omission: the magnifier is on its list of icons that keep their direction in a right-to-left
    // locale, which is why the icon set ships no mirrored variant of it to import.
    SidePanel.SEARCH -> Icons.Filled.Search
    // AutoMirrored, for the reason the explorer's own file icon is: the glyph's ragged edge
    // flips in a right-to-left locale, and the plain `Icons.Filled.Toc` is deprecated for it.
    SidePanel.TABLE_OF_CONTENTS -> Icons.AutoMirrored.Filled.Toc
    // Both references draw their own debugger with a bug glyph - JupyterLab's `bugIcon`
    // (`packages/ui-components/style/icons/bug.svg`) and VS Code's `codicon-debug-alt`/
    // `run-view-icon` are each a bug shape, and `BugReport` is Material's nearest.
    SidePanel.DEBUGGER -> Icons.Filled.BugReport
  }

/**
 * A side panel's own title bar, on the panel's surface rather than on the page's.
 *
 * **Here for the reason the label and the icon are here**: three panels draw one of these -
 * `ExplorerScreen`'s `FilesBar`, `SearchSidePanel`'s and `TableOfContentsSidePanel`'s - and each
 * already states in its own KDoc that it wears *"the same chrome as the two panels beside it"*.
 * Three copies of one expression is how that sentence stops being true.
 *
 * `TopAppBar`'s Material default is `colorScheme.surface`, which this app maps to
 * `editor.background` - so a panel that set only its `Scaffold`'s colour would draw a title strip
 * of the document's colour across the top of itself.
 *
 * ```
 * TopAppBar(title = { Text("Files") }, colors = sideBarTopAppBarColors())
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun sideBarTopAppBarColors(): TopAppBarColors =
  TopAppBarDefaults.topAppBarColors(containerColor = chromePalette.sideBarBackground)
