package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A cell appended from the footer lands where the footer stood, which is the bottom of the viewport,
 * so a cell taller than the room left there is cut off - the notebook has to scroll to it. The
 * rows here are taller than the viewport's remainder on purpose: that is the case a phone has.
 */
@RunWith(AndroidJUnit4::class)
class AppendedCellRevealTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var listState: LazyListState
  private lateinit var scope: CoroutineScope
  private var rows by mutableIntStateOf(3)

  /** Asked in the frame the row is added, as the footer's tap does: the list has not laid it out yet. */
  @Test
  fun aRowAddedInTheSameFrameAndCutOffAtTheBottomIsScrolledWhole() {
    showListScrolledToItsEnd()

    compose.runOnIdle {
      rows = 4
      scope.launch { listState.revealItem(NEW_ROW) }
    }
    compose.waitForIdle()

    compose.runOnIdle { assertTrue("the new row is cut", listState.isFullyVisible(NEW_ROW)) }
  }

  @Test
  fun aRowAlreadyWholeInViewLeavesTheListWhereItIs() {
    showListScrolledToItsEnd()
    val before = scrollPosition()

    reveal(LAST_OLD_ROW)

    assertEquals(before, scrollPosition())
  }

  private fun scrollPosition(): Pair<Int, Int> =
    compose.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }

  private fun reveal(key: Int) {
    compose.runOnIdle { scope.launch { listState.revealItem(key) } }
    compose.waitForIdle()
  }

  private fun showListScrolledToItsEnd() {
    compose.setContent {
      listState = rememberLazyListState()
      scope = rememberCoroutineScope()
      LazyColumn(state = listState, modifier = Modifier.height(VIEWPORT_DP.dp)) {
        items(rows, key = { it }) { Box(Modifier.fillMaxWidth().height(ROW_DP.dp)) }
        item(key = "footer") { Box(Modifier.fillMaxWidth().height(FOOTER_DP.dp)) }
      }
    }
    compose.runOnIdle { scope.launch { listState.scrollToItem(LAST_OLD_ROW) } }
    compose.waitForIdle()
  }

  private companion object {
    const val VIEWPORT_DP = 300
    const val ROW_DP = 200
    const val FOOTER_DP = 50
    const val LAST_OLD_ROW = 2
    const val NEW_ROW = 3
  }
}
