package com.jupydroid.app

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass

/**
 * Puts the notebook list and the open notebook side by side once the window is wide enough.
 *
 * ```
 * NavDisplay(sceneStrategy = TwoPaneSceneStrategy(twoPane = isWidthAtLeastExpanded()), …)
 * ```
 *
 * **Why a scene strategy and not `NavigableListDetailPaneScaffold`.** The scaffold puts both panes
 * inside *one* `NavEntry`, which stops the editor being a navigation entry at all - and both nav3
 * decorators key on `NavEntry.contentKey`, so the editor's `ViewModelStore` would become the
 * list's. That is defect D1 twice over: `viewModel { }` resolves on the class name and hands the
 * second notebook the first one's ViewModel, and nothing disposes the previous notebook's entry,
 * so `EditorViewModel.onCleared` never runs and its kernel socket is never closed - the guarantee
 * `notebook-session-ends` exists to prove. A scene keeps both entries alive and merely draws them
 * together, which leaves every decorator exactly where it was.
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
    if (!twoPane || entries.size < PANES) return null
    return TwoPaneScene(entries = entries.takeLast(PANES), previousEntries = entries.dropLast(1))
  }

  private companion object {
    const val PANES = 2
  }
}

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
