package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey

/**
 * What the tab strip knows that the back stack does not, kept for as long as the tabs it describes.
 *
 * The stack is saved and the documents' ViewModels are retained, so a relaunched activity - a
 * rotation relaunches it - still has every tab. Held by plain `remember` these came back empty
 * beside them: the strip reseeded its order from the stack, which is in activation order, and a
 * terminal beneath the top tab, which does not compose, lost the title its shell had reported
 * (`TabStripRecreationTest`). A `ViewModel` of the activity for [RetainedOpenDocuments]' reason.
 */
class RetainedTabStrip : ViewModel() {
  /**
   * The strip's tab order - **opening order**, not the stack's. The stack promotes an activated
   * entry to its top because that is what the scene draws, but `activateById` never moves a tab:
   * this list keeps the order the row is drawn in, reconciled against the stack each pass (closed
   * entries drop out, new ones join at the end) - see `orderedAsOpened`.
   */
  val openingOrder = mutableStateListOf<NavKey>()

  /**
   * Where each open document lives *now*, per entry. A navigation key keeps the path it was opened
   * under, because replacing it would re-create the entry and discard the editor's state; a rename
   * is reported here instead, keyed by the entry's own key since many editors are open at once.
   * `_contextsForPath` does the same - it filters live contexts by their current path, never
   * by the one a widget was created with. FileEditor keys join Editor's here: the tab strip
   * reads live paths for titles and dirty lookups; dead keys drop with the order's own reconcile.
   */
  val liveDocumentPaths = mutableStateMapOf<NavKey, String>()

  /**
   * What a tab's label is *now* where the key's own title is not live: a terminal's shell reports
   * its OSC title (`widget.ts:486-488` sends it to `title.label`, which is the tab), so the
   * screen writes it here and the strip reads it ahead of the key's static "Terminal N". Keys a
   * closed tab left behind drop out with the same reconcile that keeps the order honest.
   */
  val liveTabTitles = mutableStateMapOf<NavKey, String>()
}

/**
 * The strip's holders the activity already keeps, or new empty ones on its first composition.
 *
 * ```
 * val tabStrip = rememberRetainedTabStrip()
 * val orderedTabs = backStack.mainAreaTabs().orderedAsOpened(tabStrip.openingOrder)
 * ```
 */
@Composable
fun rememberRetainedTabStrip(): RetainedTabStrip = viewModel { RetainedTabStrip() }
