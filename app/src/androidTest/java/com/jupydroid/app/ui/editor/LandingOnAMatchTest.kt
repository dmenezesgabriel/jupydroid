package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.MatchedLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Landing on a workspace search result, and the two halves that make it one.
 *
 * **Both halves were defects here, and neither was visible to an assertion.** The reveal is what
 * brings a match out of a collapsed section; the scroll is what puts it in front of the reader.
 * A `LaunchedEffect` keyed on the cells the landing itself changes gets cancelled the moment it
 * changes them - Compose cancels a running effect when a key changes - so the scroll died
 * mid-animation and the request was never reported handled. On the tablet that read as a notebook
 * sitting on cell one while `assertVisible` on the match was green
 * (`evidence/p2x-4-open-at-the-cell/`).
 */
@RunWith(AndroidJUnit4::class)
class LandingOnAMatchTest {
  @get:Rule val compose = createComposeRule()

  private val cells = listOf(Cell("h", CellKind.MARKDOWN, "# Part A", headingCollapsed = true)) +
    (1..20).map { Cell("c$it", CellKind.CODE, "filler_$it = $it") } +
    Cell("marker", CellKind.CODE, "MARKER = \"the cat\"")

  private val uiState = MutableStateFlow(EditorUiState(cells = cells, loaded = true))

  private var handled = 0

  /** Nothing here writes; the debounce is a collaborator [NotebookCellEdits] insists on having. */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /** The marker is the **last** cell and the heading collapses every cell under it. */
  private val match = MatchedLine(line = 1, text = "MARKER = \"the cat\"", range = 10..12, cell = cells.lastIndex)

  private fun land(reveal: MatchedLine?) {
    compose.setContent {
      val state by uiState.collectAsState()
      val listState = rememberLazyListState()
      LandingOnAMatch(
        reveal = reveal,
        state = state,
        edits = NotebookCellEdits(uiState, NotebookSaveDebounce(scope) {}),
        listState = listState,
        onHandled = { handled += 1 },
      )
      // Tall items, so a notebook that never scrolled cannot have the last one on screen.
      LazyColumn(state = listState) {
        items(state.cellsOnScreen, key = { it.id }) { cell ->
          Box(modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT)) { Text(cell.source) }
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun theCellIsBroughtOutOfItsCollapsedSection() {
    land(match)

    assertFalse(uiState.value.cells.first().headingCollapsed)
    assertEquals("marker", uiState.value.selectedCellId)
  }

  /**
   * **The scroll survives the reveal**, which is the half the cancelled effect lost: expanding the
   * heading changes the cells, and an effect keyed on them would be cancelled before it scrolled.
   */
  @Test
  fun theCellIsScrolledOnToTheScreen() {
    land(match)

    compose.onNodeWithText("MARKER = \"the cat\"").assertIsDisplayed()
    compose.onNodeWithText("filler_1 = 1").assertIsNotDisplayed()
  }

  /** The request is consumed, so a later recomposition cannot drag the reader back to it. */
  @Test
  fun theRequestIsReportedHandledOnce() {
    land(match)

    assertEquals(1, handled)
  }

  /** Nothing to land on leaves the notebook where it was and reports nothing. */
  @Test
  fun noRequestLandsNowhere() {
    land(reveal = null)

    assertTrue(uiState.value.cells.first().headingCollapsed)
    assertEquals(0, handled)
  }
}

/** Tall enough that the twenty-second cell cannot be on screen with the first. */
private val ITEM_HEIGHT = 200.dp
