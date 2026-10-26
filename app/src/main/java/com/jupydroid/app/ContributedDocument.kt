package com.jupydroid.app

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.ui.shell.ContributedDocumentPane
import com.jupydroid.extension.api.DocumentContribution
import com.jupydroid.extension.api.SidePanelHost
import kotlinx.serialization.Serializable

/**
 * A file open in a tab an extension draws, because its suffix is one the extension claimed.
 *
 * One key for every extension's documents, since `:app` owns the tab strip and an extension
 * cannot add a key type; the contribution is named by its stable id, so the key round-trips
 * through saved state as [FileEditor] does.
 */
@Serializable data class ContributedDocument(val contributionId: String, val path: String) : NavKey

/**
 * The contribution that claims [path]'s suffix, in any case, or null when none does.
 *
 * ```
 * documentContributions.claiming("notes/a.chat") // the AI extension's chat
 * ```
 */
internal fun List<DocumentContribution>.claiming(path: String): DocumentContribution? =
  firstOrNull { path.endsWith(it.suffix, ignoreCase = true) }

/**
 * The [ContributedDocument] entry, in the strip's [tabbedPane] like every other document. A
 * contribution no extension gives any more draws an empty tab, which the strip can still close.
 *
 * ```
 * contributedDocumentEntry(appContainer.documentContributions, contributionHost, tabbedPane)
 * ```
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.contributedDocumentEntry(
  contributions: List<DocumentContribution>,
  host: SidePanelHost,
  tabbedPane: @Composable (@Composable () -> Unit) -> Unit,
) {
  entry<ContributedDocument>(metadata = ListDetailSceneStrategy.detailPane()) { document ->
    tabbedPane {
      contributions.find { it.id == document.contributionId }
        ?.let { ContributedDocumentPane(it, document.path, host) }
    }
  }
}
