package com.jupydroid.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How a document says how to leave it, which is a **width** question and not a document one.
 *
 * Below two panes the document is the window and leaving it goes back to what was underneath.
 * Beside a browser there is nothing to go back *to* - the browser is already on screen - so the act
 * is closing the document, which is what the reference calls it: `application:close`, labelled
 * *Close Tab* (`application-extension/src/index.tsx:309-310`).
 *
 * **`twoPane` is a parameter and not a measurement**, which is the only reason both arms can be
 * driven at all: `currentWindowAdaptiveInfo()` reads the *activity's* window, so a
 * `DeviceConfigurationOverride` around a composable cannot pick the branch and a test that tried
 * would drive one arm twice while appearing to cover both (`PaneDirective.kt:31-38`).
 */
@RunWith(AndroidJUnit4::class)
class DocumentDismissActionTest {
  @get:Rule val compose = createComposeRule()

  /**
   * One pane: the arrow, named for where it goes. "Back" alone is also the system navigation bar's
   * own button, and five Maestro flows once tapped that one instead (`evidence/u-flow-sweep/`) - so
   * the caller names the destination and this passes it through untouched.
   */
  @Test
  fun belowTwoPanesItIsTheArrowNamedForWhereItGoes() {
    showAction(twoPane = mutableStateOf(false))

    compose.onNodeWithContentDescription(BACK_LABEL).assertIsDisplayed()
    compose.onAllNodesWithContentDescription(CLOSE_DOCUMENT).assertCountEquals(0)
  }

  /** Two panes: the glyph is Close and it says so, because the browser is already on screen. */
  @Test
  fun atTwoPanesItIsCloseAndSaysSo() {
    showAction(twoPane = mutableStateOf(true))

    compose.onNodeWithContentDescription(CLOSE_DOCUMENT).assertIsDisplayed()
    compose.onAllNodesWithContentDescription(BACK_LABEL).assertCountEquals(0)
  }

  /**
   * **The same action either way**, and the width alone decides which is drawn: closing beside a
   * browser pops the entry that going back pops. What changes is what the user is told they are
   * doing, not what happens - so one lambda, driven through both arms of one composition.
   */
  @Test
  fun bothArmsRunTheSameActionAndTheWidthAloneSwapsThem() {
    var dismissed = 0
    val twoPane = mutableStateOf(false)
    showAction(twoPane = twoPane, onDismiss = { dismissed++ })

    compose.onNodeWithContentDescription(BACK_LABEL).performClick()
    compose.runOnIdle { twoPane.value = true }
    compose.onNodeWithContentDescription(CLOSE_DOCUMENT).performClick()

    compose.runOnIdle { assertEquals(2, dismissed) }
  }

  private fun showAction(twoPane: androidx.compose.runtime.MutableState<Boolean>, onDismiss: () -> Unit = {}) {
    compose.setContent {
      JupyterDroidTheme {
        DocumentDismissAction(twoPane = twoPane.value, backDescription = BACK_LABEL, onDismiss = onDismiss)
      }
    }
  }

  private companion object {
    /** The notebook's own, so the arm under test is the one three bars actually pass. */
    const val BACK_LABEL = "Back to notebooks"
  }
}
