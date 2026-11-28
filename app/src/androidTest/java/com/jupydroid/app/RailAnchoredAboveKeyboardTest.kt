package com.jupydroid.app

import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.ui.shell.LEAVE_WORKSPACE
import com.jupydroid.app.ui.shell.OPEN_SETTINGS
import com.jupydroid.app.ui.shell.SEARCH_WORKSPACE_BOX
import com.jupydroid.app.ui.softKeyboardTop
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rail's foot with the soft keyboard up: home and Settings must not move.
 *
 * The reported defect: `WindowKeyRowHosted`'s `imePadding` paid the inset for everything inside it,
 * and the rail was inside it - the keyboard's height came off the rail's, so the foot rows rode
 * ~600px up the window and the panel region shrank to a sliver that clipped the last destinations
 * (measured on SM-X216B: "Chat" drawn 32px tall, the foot from y449 where it belongs near y1180).
 * The rail is beside the whole shell - it is not typed into, so the inset is not its to pay.
 *
 * Drives the real `MainActivity`: the rail's placement relative to the key-row host is the app's
 * wiring, which no `setContent` composition stands in for. Meaningful only where the rail exists -
 * a two-pane window, the `SM-X216B`; skipped on the phone emulator, which has no rail to anchor.
 */
@RunWith(AndroidJUnit4::class)
class RailAnchoredAboveKeyboardTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun theRailsFootItemsDoNotMoveWhenTheKeyboardOpens() {
    enterBaseSpaceOnThisDevice()
    assumeTrue(
      "the activity bar exists only beside two panes; this window is one pane",
      compose.onAllNodesWithContentDescription(OPEN_SETTINGS).fetchSemanticsNodes().isNotEmpty(),
    )
    val settingsBefore = railFootBounds(OPEN_SETTINGS)
    val spacesBefore = railFootBounds(LEAVE_WORKSPACE)

    compose.onNodeWithContentDescription("Search").performClick()
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performClick()

    compose.waitUntil(KEYBOARD_TIMEOUT_MILLIS) { softKeyboardTop() != null }

    assertEquals(
      "the Spaces row moved when the keyboard opened - it must stay anchored to the rail's bottom",
      spacesBefore,
      railFootBounds(LEAVE_WORKSPACE),
    )
    assertEquals(
      "the Settings row moved when the keyboard opened - it must stay anchored to the rail's bottom",
      settingsBefore,
      railFootBounds(OPEN_SETTINGS),
    )
  }

  /**
   * `enterBaseSpace` with room for the real server: its 5s waits fit the faked containers the
   * other navigation tests run, and a cold `jupyter_server` boot on a real device is slower.
   */
  private fun enterBaseSpaceOnThisDevice() {
    compose.waitUntil(ENTER_TIMEOUT_MILLIS) {
      compose.onAllNodesWithContentDescription(OPEN_BASE_SPACE).fetchSemanticsNodes().isNotEmpty()
    }
    compose.runCatching { compose.onNodeWithContentDescription(runBaseSpace()).performClick() }
    compose.waitUntil(ENTER_TIMEOUT_MILLIS) {
      compose.onAllNodesWithContentDescription(OPEN_BASE_SPACE)
        .fetchSemanticsNodes()
        .any { SemanticsProperties.Disabled !in it.config }
    }
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).performClick()
    compose.waitUntil(ENTER_TIMEOUT_MILLIS) {
      compose.onAllNodesWithContentDescription("Files").fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun runBaseSpace() = "Run ${BASE_SPACE.name} (${BASE_SPACE.id})"

  private fun railFootBounds(label: String): Rect =
    compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInWindow.let {
      Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
    }

  private companion object {
    const val KEYBOARD_TIMEOUT_MILLIS = 10_000L
    const val ENTER_TIMEOUT_MILLIS = 120_000L
  }
}
