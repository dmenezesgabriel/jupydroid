package com.jupydroid.app.ui.shell

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.orderedAsOpened
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `saveBeforeClose` when the ask does not come from the tab itself - the Running panel's row
 * close and its "Close All" (`running:close`, `opentabs.ts:82-96`) both reach the gate above the
 * strip, and several asks can land in one pass where a single `closing` slot would drop all but
 * the last.
 *
 * The reference answers with one dialog per dirty widget, all up at once; a phone has one modal
 * surface, so the gate asks **in turn** - the second document's question behind the first's, which
 * is `closeAll`'s iteration order and the order the eye expects.
 */
@RunWith(AndroidJUnit4::class)
class MainAreaCloseGateTest {
  @get:Rule val compose = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id), Editor(FIRST), Editor(SECOND))
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()
  private val order = mutableStateListOf<NavKey>()
  private var closeTab: (NavKey) -> Unit = {}

  /** Two dirty tabs asked to leave in one pass - each still gets its own question. */
  @Test
  fun closingTwoDirtyTabsAsksForEachInTurn() {
    open.opened(UnsavedDocument(FIRST))
    open.opened(UnsavedDocument(SECOND))
    show()

    compose.runOnIdle {
      closeTab(Editor(FIRST))
      closeTab(Editor(SECOND))
    }

    compose.onNodeWithText("Save changes in \"$FIRST\" before closing?").assertIsDisplayed()
    compose.onNodeWithText("Discard").performClick()
    compose.onNodeWithText("Save changes in \"$SECOND\" before closing?").assertIsDisplayed()
    compose.onNodeWithText("Discard").performClick()
    compose.waitForIdle()

    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id)), backStack.toList())
  }

  /** A clean tab beside a dirty one still leaves immediately - no queue position to wait for. */
  @Test
  fun aCleanTabClosesWithoutQueueingBehindADirtyOne() {
    open.opened(UnsavedDocument(FIRST))
    show()

    compose.runOnIdle {
      closeTab(Editor(FIRST))
      closeTab(Editor(SECOND))
    }

    compose.onNodeWithText("Save changes in \"$FIRST\" before closing?").assertIsDisplayed()
    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id), Editor(FIRST)), backStack.toList())
  }

  /**
   * A second ask for a tab already waiting is the same ask, not a second turn - a double "Close
   * All" would otherwise put the same document's dialog up twice and close it once.
   */
  @Test
  fun theSameTabAskedTwiceWaitsOnce() {
    open.opened(UnsavedDocument(FIRST))
    show()

    compose.runOnIdle {
      closeTab(Editor(FIRST))
      closeTab(Editor(FIRST))
    }
    compose.onNodeWithText("Save changes in \"$FIRST\" before closing?").assertIsDisplayed()
    compose.onNodeWithText("Discard").performClick()
    compose.waitForIdle()

    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id), Editor(SECOND)), backStack.toList())
    compose.onNodeWithText("Save your work").assertDoesNotExist()
  }

  /** The gate mounted the way `MainNavigation` mounts it - over a real stack of documents. */
  private fun show() {
    compose.setContent {
      JupyterDroidTheme {
        val tabs = backStack.mainAreaTabs().orderedAsOpened(order)
        LaunchedEffect(tabs) {
          order.clear()
          order.addAll(tabs)
        }
        closeTab = rememberMainAreaCloseGate(backStack, tabs, open, paths)
      }
    }
  }

  /**
   * A document that owes a write - what makes a close stop at the dialog rather than go through.
   * [discardChanges] records rather than writes, so the test can tell the dialog's button ran.
   */
  private class UnsavedDocument(override val path: String) : OpenDocument {
    override val openedAs: DocumentOpening = DocumentOpening(path)
    override val dirty: StateFlow<Boolean> = MutableStateFlow(true)
    var discarded = false
      private set

    override fun saveNow(onSaved: () -> Unit) = onSaved()
    override fun discardChanges() {
      discarded = true
    }

    override fun reloadFromDisk() = Unit

    override fun sourceOf(cell: Int?): String? = null
    override fun applyEdits(cell: Int?, edits: List<TextEdit>) = Unit
  }

  private companion object {
    const val FIRST = "a.ipynb"
    const val SECOND = "b.ipynb"
  }
}
