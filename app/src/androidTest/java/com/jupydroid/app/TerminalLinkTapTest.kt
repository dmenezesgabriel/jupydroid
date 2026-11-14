package com.jupydroid.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeExternalLinkOpener
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.terminal.TerminalPaneScreen
import com.jupydroid.app.ui.terminal.TerminalPaneViewModel
import com.jupydroid.app.ui.terminal.terminalCellMetrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tap on a web address the terminal printed, or on a word under an `OSC 8` link to one, asks
 * "Open Link?" before anything leaves the app, as
 * JupyterLab's `activateLink` does (jupyterlab@7f0e6906 packages/terminal/src/widget.ts), and a tap
 * anywhere else asks nothing. Asked of the real screen, because the address pattern runs on ART's
 * ICU engine and the tap competes with the input field for the same press. The opener is
 * [FakeExternalLinkOpener] behind the app's own [RenderedLinksFollowed]. Whether the keyboard rises
 * is not asked here: the field is focused from the moment it is composed, so focus does not
 * change, and the keyboard is the platform's - the device flow reads it from `dumpsys input_method`.
 */
@RunWith(AndroidJUnit4::class)
class TerminalLinkTapTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val gateway = FakeTerminalGateway()
  private val session = runBlocking { gateway.open() }
  private val viewModel = TerminalPaneViewModel(session, gateway)
  private val opener = FakeExternalLinkOpener()
  private var cell by mutableStateOf<Size?>(null)

  @Test
  fun aTapOnAPrintedAddressAsksBeforeOpeningIt() {
    showTheTerminal(text = "go https://jupyter.org/x")

    compose.pressTerminalCell(cell, column = 8) { click(it) }

    compose.onNodeWithText("Open Link?").assertIsDisplayed()
    compose.onNodeWithText("Do you want to navigate to https://jupyter.org/x?", substring = true).assertIsDisplayed()
    assertEquals(emptyList<String>(), opener.opened)
  }

  @Test
  fun openHandsTheAddressToTheOpener() {
    showTheTerminal(text = "go https://jupyter.org/x")
    compose.pressTerminalCell(cell, column = 8) { click(it) }

    compose.onNodeWithText("Open").performClick()

    compose.onNodeWithText("Open Link?").assertDoesNotExist()
    assertEquals(listOf("https://jupyter.org/x"), opener.opened)
  }

  @Test
  fun cancelOpensNothing() {
    showTheTerminal(text = "go https://jupyter.org/x")
    compose.pressTerminalCell(cell, column = 8) { click(it) }

    compose.onNodeWithText("Cancel").performClick()

    compose.onNodeWithText("Open Link?").assertDoesNotExist()
    assertEquals(emptyList<String>(), opener.opened)
  }

  @Test
  fun aTapBesideTheAddressAsksNothing() {
    showTheTerminal(text = "go https://jupyter.org/x")

    compose.pressTerminalCell(cell, column = 0) { click(it) }

    compose.onNodeWithText("Open Link?").assertDoesNotExist()
  }

  /** `TermuxTerminalViewClient.onSingleTapUp` (termux-app `3df69d1d`): a tap while selecting only ends the selection. */
  @Test
  fun aTapOnAnAddressWhileSelectingOnlyEndsTheSelection() {
    showTheTerminal(text = "go https://jupyter.org/x")
    compose.pressTerminalCell(cell, column = 0) { longClick(it) }
    assertNotNull(viewModel.selector.selection.value)

    compose.pressTerminalCell(cell, column = 8) { click(it) }

    assertEquals(null, viewModel.selector.selection.value)
    compose.onNodeWithText("Open Link?").assertDoesNotExist()
  }

  /** An `OSC 8` link opens its own address, which is nowhere on screen, after the same question. */
  @Test
  fun aTapOnAWordUnderAnOsc8LinkOpensItsAddress() {
    showTheTerminal(text = "go \u001B]8;;https://jupyter.org/y\u0007docs\u001B]8;;\u0007")
    compose.pressTerminalCell(cell, column = 4) { click(it) }

    compose.onNodeWithText("Do you want to navigate to https://jupyter.org/y?", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Open").performClick()

    assertEquals(listOf("https://jupyter.org/y"), opener.opened)
  }

  private fun showTheTerminal(text: String) {
    compose.setContent {
      JupyterDroidTheme {
        cell = terminalCellMetrics()
        RenderedLinksFollowed(opener) {
          TerminalPaneScreen(session = session, terminalGateway = gateway, viewModel = viewModel)
        }
      }
    }
    compose.runOnUiThread {
      val bytes = text.toByteArray()
      viewModel.emulator.append(bytes, bytes.size)
    }
  }
}
