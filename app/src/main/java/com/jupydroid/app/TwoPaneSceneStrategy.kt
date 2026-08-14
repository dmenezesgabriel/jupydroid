package com.jupydroid.app

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass

/**
 * Puts the file surface and the document it opened side by side once the window is wide enough.
 *
 * ```
 * NavDisplay(sceneStrategy = TwoPaneSceneStrategy(twoPane = isWidthAtLeastExpanded()), …)
 * ```
 *
 * **What pairs.** The two panes are a surface and *a document that surface opens*, never any two
 * entries. Which entry is which is declared in metadata when the entry providers are built
 * (`Navigation.kt`), because `NavEntry` does not expose its key - the pairing rule has to read
 * what the builder chose to say. The explorer opens notebooks and plain files, so it pairs with
 * an [Editor] or a [FileEditor]; it is the only surface there is. Every stack the app can reach:
 *
 * | stack                       | panes                |
 * |-----------------------------|----------------------|
 * | Explorer                    | Explorer             |
 * | Explorer, Editor            | Explorer\|Editor      |
 * | Explorer, FileEditor        | Explorer\|FileEditor  |
 *
 * Three, because `NotebookBackStack` replaces the open document rather than stacking on it, so a
 * document is never beneath another document.
 *
 * **The defect that made the rule explicit** was a fourth shape that no longer exists: back when
 * a Recents screen was the first destination, "the last two entries" put it beside the file
 * browser - two surfaces, neither of which opens the other. That is why the pairing reads
 * metadata rather than position, and the reason outlives the screen that exposed it.
 *
 * **Why a scene strategy and not `NavigableListDetailPaneScaffold`.** The scaffold puts both
 * panes inside *one* `NavEntry`, which stops the editor being a navigation entry at all - and
 * both nav3 decorators key on `NavEntry.contentKey`, so the editor's `ViewModelStore` would
 * become the list's. That is defect D1 twice over: `viewModel { }` resolves on the class name and
 * hands the second notebook the first one's ViewModel, and nothing disposes the previous
 * notebook's entry, so `EditorViewModel.onCleared` never runs and its kernel socket is never
 * closed - the guarantee `notebook-session-ends` exists to prove. A scene keeps both entries alive
 * and merely draws them together, which leaves every decorator exactly where it was.
 *
 * The library answer is `androidx.compose.material3.adaptive:adaptive-navigation3`, which has no
 * stable release (`1.3.0-rc01`) and whose POM requires `adaptive-navigation` **strictly** at that
 * version - adopting it would move the whole adaptive family, `currentWindowAdaptiveInfo()`
 * included, off the Compose BOM and onto pinned release candidates. This is ~40 lines against
 * `SceneStrategy`, which is public API in the released `navigation3-ui` we already depend on.
 * If that artifact goes stable, this is the thing to delete.
 *
 * Returning null is not a failure: `NavDisplay` falls through to the single-pane strategy, which
 * is what a compact window and a list with nothing open both want.
 */
class TwoPaneSceneStrategy<T : Any>(private val twoPane: Boolean) : SceneStrategy<T> {
  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    if (!twoPane || !pairsIntoTwoPanes(entries)) return null
    return TwoPaneScene(entries = entries.takeLast(PANES), previousEntries = entries.dropLast(1))
  }

  /** Whether the two top entries are a surface and a document that surface opens - table above. */
  private fun pairsIntoTwoPanes(entries: List<NavEntry<T>>): Boolean {
    if (entries.size < PANES) return false
    val surface = entries[entries.size - 2]
    val role = surface.metadata[PANE_ROLE]
    val kind = entries.last().metadata[PANE_DOCUMENT_KIND]
    val opens = surface.metadata[PANE_SURFACE_OPENS] as? List<*>
    return role == PANE_ROLE_SURFACE && kind is String && opens?.contains(kind) == true
  }

  private companion object {
    const val PANES = 2
  }
}

/** The metadata vocabulary [TwoPaneSceneStrategy] pairs on, declared in `Navigation.kt`. */
internal const val PANE_ROLE = "paneRole"
internal const val PANE_ROLE_SURFACE = "surface"
internal const val PANE_ROLE_DOCUMENT = "document"
internal const val PANE_DOCUMENT_KIND = "documentKind"
internal const val PANE_KIND_NOTEBOOK = "notebook"
internal const val PANE_KIND_FILE = "file"
internal const val PANE_SURFACE_OPENS = "opens"

/** Explorer opens both document kinds; see [TwoPaneSceneStrategy]'s table. */
internal val EXPLORER_PANE_METADATA =
  mapOf(
    PANE_ROLE to PANE_ROLE_SURFACE,
    PANE_SURFACE_OPENS to listOf(PANE_KIND_NOTEBOOK, PANE_KIND_FILE),
  )

/**
 * Whether this window has room for both panes: `WIDTH_DP_EXPANDED_LOWER_BOUND`, 840dp.
 *
 * Expanded rather than medium, and the list pane's own width is the argument: 360dp of list plus
 * a divider leaves 239dp for the notebook in a 600dp window, which is narrower than the phone the
 * single-pane layout was designed for. At 840dp the notebook keeps 479dp.
 *
 * **A width rule, never a device one.** The tablet in a split-screen window is a compact window
 * and gets one pane, and the class changes under the layout as the divider is dragged.
 */
@Composable
internal fun isWindowWideEnoughForTwoPanes(): Boolean =
  currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(EXPANDED_WIDTH_DP)

private const val EXPANDED_WIDTH_DP = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
