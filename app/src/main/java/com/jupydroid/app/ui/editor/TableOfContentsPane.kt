package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * `toc:show-panel` where the editor has room to keep it open - a column beside the notebook rather
 * than a surface over it.
 *
 * This is the reference's own shape: its ToC is a sidebar panel added to the shell
 * (`packages/toc-extension/src/index.ts:309`, `shell.add(toc, 'left', { rank: 400 })`), and
 * `toc:show-panel` only activates it (`:161-172`). What that buys over [TableOfContentsPanel]'s
 * overlay is what a panel is for: **a pick does not close it**, so jumping around a long notebook
 * costs one tap per jump, and the structure stays on screen while the document scrolls.
 *
 * Which of the two an editor gets is [tocPresentationFor], measured against the editor's own
 * width - never the window's, since in the two-pane layout the explorer has already spent 360dp of
 * it.
 *
 * **Not a `ListDetailSceneStrategy.extraPane()`**, which is what `docs/PLAN.md` assumed and what
 * reading the library ruled out: an extra pane is a separate `NavEntry`, and every entry here owns
 * its own `ViewModelStore` (`docs/architecture.md` §4) - so it could reach neither the notebook's
 * `EditorViewModel` for the headings nor its `LazyListState` to scroll. See `docs/DECISIONS.md`.
 */
@Composable
fun TableOfContentsPane(
  headings: List<TocHeading>,
  onHeadingSelected: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxHeight()) {
    VerticalDivider()
    Column(modifier = Modifier.width(TOC_PANE_WIDTH)) {
      TableOfContentsPaneHeader(onClose)
      HorizontalDivider()
      Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TableOfContentsRows(headings, onHeadingSelected)
      }
    }
  }
}

@Composable
private fun TableOfContentsPaneHeader(onClose: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
  ) {
    Text(text = "Table of contents", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.weight(1f))
    // The name goes on the button rather than on the icon inside it - the rule a flow cost this
    // project in P2b, and the same word the View menu's own row uses so a screen reader and a
    // Maestro flow address one string.
    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = HIDE_TABLE_OF_CONTENTS }) {
      Icon(Icons.Filled.Close, contentDescription = null)
    }
  }
}

/**
 * Wide enough for a heading and its indent, narrow enough to leave the notebook the larger half at
 * every width [tocPresentationFor] draws this at: 840dp of editor keeps 536dp of notebook.
 */
internal val TOC_PANE_WIDTH = 280.dp
