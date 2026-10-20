package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.extension.api.KeyRowPublished
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where a published key row is drawn: across the window, whatever pane published it.
 *
 * `WindowKeyRowSlotTest` argues which row the slot holds; whether the row is *laid out* at the
 * window's width rather than the pane's is a question about layout, which only a composition answers.
 */
@RunWith(AndroidJUnit4::class)
class WindowKeyRowTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aRowPublishedFromANarrowPaneSpansTheWindow() {
    compose.setContent {
      WindowKeyRowHosted {
        Row(modifier = Modifier.fillMaxSize()) {
          Box(modifier = Modifier.width(SIDE_PANE))
          Box(modifier = Modifier.weight(1f)) { KeyRowPublished { TaggedRow() } }
        }
      }
    }

    assertEquals(compose.onRoot().getBoundsInRoot().width, compose.onNodeWithTag(ROW).getBoundsInRoot().width)
  }

  @Test
  fun withoutAHostTheRowIsDrawnWhereItWasPublished() {
    compose.setContent {
      Box(modifier = Modifier.width(SIDE_PANE)) { KeyRowPublished { TaggedRow() } }
    }

    assertEquals(SIDE_PANE, compose.onNodeWithTag(ROW).getBoundsInRoot().width)
  }

  @Test
  fun theRowGoesWhenItsPublisherLeaves() {
    var typing by mutableStateOf(true)
    compose.setContent {
      WindowKeyRowHosted {
        if (typing) KeyRowPublished { TaggedRow() }
      }
    }

    typing = false

    compose.onNodeWithTag(ROW).assertDoesNotExist()
  }

  @Composable
  private fun TaggedRow() {
    Box(modifier = Modifier.fillMaxWidth().height(48.dp).testTag(ROW))
  }

  private companion object {
    const val ROW = "published_row"
    val SIDE_PANE = 200.dp
  }
}
