package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.app.DocumentOutline
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.FocusRingIconButton
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.editor.TableOfContentsRows
import com.jupydroid.app.ui.editor.TocFolds
import com.jupydroid.app.ui.editor.tocRows
import com.jupydroid.app.ui.editor.tocSectionOwners
import com.jupydroid.app.ui.editor.tocSomeExpanded

/**
 * `shell.add(toc, 'left', { rank: 400 })` - the open document's structure as a **sidebar panel**,
 * where this app used to draw a column on the right of the notebook.
 *
 * It reads a [DocumentOutline] and derives nothing, because it cannot: the panel is in the list
 * pane and the headings are worked out in the notebook's own `NavEntry`. That is the whole reason
 * `OpenDocumentOutline` exists - see it for the reference's own version of the same seam.
 *
 * **A pick does not close it**, which is what a panel buys over the overlay
 * `NotebookActionsMenu` still shows where there is no side area: jumping twice costs two taps
 * rather than four, and the structure stays up while the document scrolls.
 *
 * **The same chrome as the panel beside it**: a `Scaffold` and a `TopAppBar`, exactly as
 * `ExplorerScreen`'s `FilesBar`. That is not
 * only symmetry - the bar is also what pads the panel out of the status bar, and drawn as a bare
 * `Column` its header sat on top of the clock.
 *
 * @param outline the open document's, or null when nothing that has one is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSidePanel(outline: DocumentOutline?, modifier: Modifier = Modifier) {
  // Keyed on the document, so a fold set cannot outlive what it folds - `TocFolds`' own lifetime,
  // said here because the panel now outlives the notebook where the editor's copy did not. On the
  // title rather than on the outline itself, which is republished on every edit to a heading.
  val folds = remember(outline?.title) { TocFolds() }
  val headings = outline?.headings.orEmpty()
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = chromePalette.sideBarBackground,
    topBar = {
      TopAppBar(
        colors = sideBarTopAppBarColors(),
        // Named for the **document**, which is the reference's own rule -
        // `this._title.setTitle(this._model?.title ?? this._trans.__('Table of Contents'))`
        // (`packages/toc/src/panel.ts:77-80`) - with the panel's own name as the fallback.
        title = {
          Text(
            text = outline?.title ?: SidePanel.TABLE_OF_CONTENTS.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        actions = {
          CollapseAllEntries(
            // Absent where nothing nests, rather than drawn and dead: a document of H1s has no fold
            // to make. The reference draws it unconditionally, which its wider toolbar can afford.
            someExpanded = headings.tocSomeExpanded(folds.collapsed)
              .takeIf { headings.tocSectionOwners().isNotEmpty() },
            onToggleAllCollapsed = { folds.toggleAll(headings) },
          )
        },
      )
    },
  ) { padding ->
    // `if (!this.model || this.model.headings.length === 0)` - one placeholder for both "nothing is
    // open" and "nothing has headings" (`packages/toc/src/treeview.tsx:31-40`).
    if (outline == null || headings.isEmpty()) {
      NoHeadingsPlaceholder(modifier = Modifier.padding(padding))
      return@Scaffold
    }
    Column(modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
      TableOfContentsRows(
        rows = headings.tocRows(folds.collapsed, outline.numbering),
        onChoose = outline.jumpTo,
        onToggleCollapsed = folds::toggle,
      )
    }
  }
}

/**
 * `toc:toggle-collapse`, which is Collapse All and Expand All under one control - and the panel's
 * own toolbar is where the reference puts it too
 * (`packages/toc-extension/src/index.ts:260-306`).
 *
 * [someExpanded] is null when there is nothing foldable at all, and the control is then absent.
 */
@Composable
private fun CollapseAllEntries(someExpanded: Boolean?, onToggleAllCollapsed: () -> Unit) {
  if (someExpanded == null) return
  // One control for both halves of one command, named for the half the next tap performs -
  // `someExpanded` is the reference's own predicate (`index.ts:174-210`), and a name that flips is
  // what a Maestro flow can read where a toggled state is not.
  FocusRingIconButton(
    onClick = onToggleAllCollapsed,
    modifier = Modifier.semantics {
      contentDescription = if (someExpanded) COLLAPSE_ALL_TOC_ENTRIES else EXPAND_ALL_TOC_ENTRIES
    },
  ) {
    Icon(
      imageVector = if (someExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
      contentDescription = null,
    )
  }
}

/**
 * The reference's own placeholder, in this app's sentence case: a headline and a line saying what
 * would fill the panel (`packages/toc/src/panel.ts:32-36`). The wording is deliberately about
 * *documents* rather than notebooks, because that is what the reference promises.
 */
@Composable
private fun NoHeadingsPlaceholder(modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth().padding(Spacing.lg)) {
    Text(text = "No headings", style = MaterialTheme.typography.titleSmall)
    Text(
      text = "The table of contents shows headings in notebooks and supported files.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = Spacing.sm),
    )
  }
}

/**
 * `toc:toggle-collapse`'s two names. Distinct from `COLLAPSE_ALL_HEADINGS` on purpose: that folds
 * the **document's** sections and this folds the table of contents' own tree, and two controls a
 * tap apart must not answer to one string.
 */
internal const val COLLAPSE_ALL_TOC_ENTRIES = "Collapse all entries"

/** The same command from the other side - see [COLLAPSE_ALL_TOC_ENTRIES]. */
internal const val EXPAND_ALL_TOC_ENTRIES = "Expand all entries"
