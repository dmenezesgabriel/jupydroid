package com.jupydroid.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.ExplorerSettings
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.ExplorerSettingsRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What was opened, and whether the section showing it is shut.
 *
 * @property documents the whole history, newest first - how much of it a given directory shows is
 *   [com.jupydroid.app.domain.model.shownWhileBrowsing]'s to say, at the point that knows the
 *   directory
 * @property collapsed whether the section is showing its heading and none of its rows
 */
data class RecentsUiState(val documents: List<RecentDocument> = emptyList(), val collapsed: Boolean = false)

/**
 * The Recents section: a record of what was opened, and the one control over it.
 *
 * **Its own state holder because it is its own thing**, which is how the reference is arranged and
 * what [recentsSection] already says in as many words: recents belong to `docmanager` and are
 * surfaced from `mainmenu-extension`, never from `filebrowser`. Drawing the list inside the browser
 * is X2h's adaptation to a phone with no menu bar; letting the *browser's* state holder own it was
 * the adaptation bleeding through, and detekt counting [ExplorerViewModel]'s functions is what said
 * so out loud.
 *
 * ```
 * RecentsViewModel(recentDocuments, explorerSettings)
 *   .open(document, onFolder = explorer::goTo, onDocument = openFile)
 * ```
 */
class RecentsViewModel(
  private val recentDocuments: RecentDocumentsRepository,
  /**
   * Where the collapse lives between launches. Read here rather than held in this class, so the
   * store is the single source of truth for it - which is what makes a section shut before the
   * process died still shut after it.
   */
  private val explorerSettings: ExplorerSettingsRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(RecentsUiState())
  val uiState: StateFlow<RecentsUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      recentDocuments.observeRecents().collect { opened -> _uiState.update { it.copy(documents = opened) } }
    }
    viewModelScope.launch {
      explorerSettings.observeSettings().collect { stored ->
        _uiState.update { it.copy(collapsed = stored.recentsCollapsed) }
      }
    }
  }

  /**
   * A recent folder moves the browser; only a document is handed to an editor.
   *
   * The reference's `openRecent` makes the same split - a `contentType: 'directory'` goes to the
   * file browser's `openPath` and everything else to `docmanager:open`
   * (`packages/mainmenu-extension/src/recents.ts`). Which editor a document gets is not decided
   * here, for the same reason [ExplorerViewModel.open] does not decide it - and *where* a folder
   * goes is not decided here either, because the browser is what moves.
   */
  fun open(document: RecentDocument, onFolder: (String) -> Unit, onDocument: (String) -> Unit) {
    if (document.kind == WorkspaceEntryKind.DIRECTORY) onFolder(document.path) else onDocument(document.path)
  }

  /**
   * Shuts the section, or opens it again.
   *
   * `setExpanded(!this.isExpanded())`, which the reference runs from the header itself on a click
   * *or a tap* (`paneview.ts:285-292`) - so one method for both directions rather than two named
   * for the outcomes.
   */
  fun toggle() {
    // Written through rather than held: the reference's `setCollapsed` stores the view's state on
    // every change (`viewContainerModel.ts:456-461`), and the collector above is what puts it back
    // on screen - so there is one truth and not two that can drift.
    viewModelScope.launch { explorerSettings.update(ExplorerSettings(recentsCollapsed = !_uiState.value.collapsed)) }
  }

  /** `docmanager:clear-recents`, which the reference offers from the same submenu. */
  fun clear() {
    viewModelScope.launch { recentDocuments.clear() }
  }

  /**
   * Drops the entries whose files have since gone.
   *
   * The reference drops exactly those - `_isValid` treats a 404 as invalid and
   * `RecentsMenu.onBeforeAttach` re-validates every time the list is about to be shown
   * (`mainmenu-extension/src/recents.ts`). Ours is always on screen, so "about to be shown" is
   * whenever the browser refreshes.
   */
  fun revalidate() {
    viewModelScope.launch { recentDocuments.revalidate() }
  }
}
