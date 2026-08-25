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
import com.jupydroid.app.SidePanel

/**
 * `workbench.view.search` - the workspace searched, where P2i's find bar searches one document.
 *
 * **Named and empty as of P2x-2**, which is the whole of this sub-step: the rail grew a third item
 * and this is where it lands. The query box, the replace toggle, the three option toggles, the
 * include / exclude fields, the results grouped by file and the result-cap message are all P2x-3,
 * and they read `WorkspaceSearcher` - already built, already handed to `AppContainer`
 * (`JupydroidApplication.kt:75`), and drawn by nothing yet.
 *
 * **No placeholder, and that is the reference rather than an omission.** `TableOfContentsSidePanel`
 * has one because `treeview.tsx:31-40` draws one; VS Code's search view with an empty query is its
 * input box and nothing else - there is no "search your workspace" copy to transcribe, and
 * inventing one now would be a string P2x-3 immediately deletes.
 *
 * **The same chrome as the two panels beside it** - a `Scaffold` and a `TopAppBar`, for the reason
 * `TableOfContentsSidePanel`'s own doc gives: the bar is also what pads the panel out of the
 * status bar, and drawn as a bare `Column` its header sat on top of the clock.
 *
 * ```
 * SidePanel.SEARCH -> SearchSidePanel()
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSidePanel(modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { TopAppBar(title = { Text(SidePanel.SEARCH.label) }) },
  ) { padding ->
    // The body P2x-3 fills - see this file's own doc for why nothing stands in for it meanwhile.
    // Empty, but **not** an ignored `padding`: dropping the inset the bar reserves is what
    // `UnusedMaterial3ScaffoldPaddingParameter` refuses, and it is right to - the first thing P2x-3
    // puts here would otherwise be drawn under the title.
    Box(modifier = Modifier.padding(padding).fillMaxSize())
  }
}
