package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.RunningTab
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.AtFontScale
import com.jupydroid.app.ui.PHONE_WIDTH
import com.jupydroid.app.ui.isCut
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the Running panel's rows put their close button on the narrowest phone under a large font
 * scale: one column, inside the screen, whatever the length of the name beside it. The panel's
 * behaviour at the default size is `RunningSidePanelTest`'s.
 *
 * The label took all the width it asked for and the button got the remainder, so one long name pushed
 * its button past the screen's edge and out of line with the other rows'.
 */
@RunWith(AndroidJUnit4::class)
class RunningSidePanelFontScaleTest {
  @get:Rule val compose = createComposeRule()

  private val registry = KernelSessionRegistry()
  private val kernelGateway = FakeKernelGateway(registry)
  private val kernelSessions = FakeKernelSessions(registry)
  private val terminalGateway = FakeTerminalGateway()

  @Test
  fun everyCloseButtonStaysInsideAPhoneAtTwiceTheFontScale() {
    showOnAPhoneAt2x(listOf(SHORT_A, LONG_NAME, SHORT_B))

    val screenWidth = with(compose.density) { PHONE_WIDTH.toPx() }
    listOf(SHORT_A, LONG_NAME, SHORT_B).forEach { title ->
      val bounds = closeButton(title)
      assertTrue("Close: $title at $bounds leaves the 0..$screenWidth px screen", bounds.right <= screenWidth)
    }
  }

  @Test
  fun closeButtonsShareOneColumnWhateverTheNameLength() {
    showOnAPhoneAt2x(listOf(SHORT_A, LONG_NAME, SHORT_B))

    val lefts = listOf(SHORT_A, LONG_NAME, SHORT_B).map { closeButton(it).left }
    assertEquals("close buttons start at $lefts px, not one column", 1, lefts.toSet().size)
  }

  @Test
  fun aLongNameIsCutWhileShortNamesAreNot() {
    showOnAPhoneAt2x(listOf(SHORT_A, LONG_NAME))

    assertTrue("$LONG_NAME should be ellipsized", labelOf(LONG_NAME).isCut())
    assertFalse("$SHORT_A should fit whole", labelOf(SHORT_A).isCut())
  }

  @Test
  fun aKernelSessionsIdStaysBesideItsNameAndInsideThePhone() {
    runBlocking { kernelGateway.connect(BASE_SPACE, LONG_NAME) }
    showOnAPhoneAt2x(emptyList())
    val kernelIdShown = hasText("(", substring = true)
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodes(kernelIdShown, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    val screenWidth = with(compose.density) { PHONE_WIDTH.toPx() }
    val kernelId = compose.onNode(kernelIdShown, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val closeLeft = closeButton(LONG_NAME, KERNEL).left
    assertTrue("the id at $kernelId leaves the 0..$screenWidth px screen", kernelId.right <= screenWidth)
    assertTrue("the id at $kernelId lies past its row's close button at $closeLeft px", kernelId.right <= closeLeft)
  }

  private fun showOnAPhoneAt2x(tabTitles: List<String>) {
    val tabs = tabTitles.map { RunningTab(Editor(it), it) }
    compose.setContent {
      JupydroidTheme {
        Box(Modifier.requiredWidth(PHONE_WIDTH)) {
          AtFontScale(2f) {
            RunningSidePanel(
              kernelSessions = kernelSessions,
              terminalGateway = terminalGateway,
              openTabs = tabs,
              onActivateTab = {},
              onCloseTab = {},
              saveAllEnabled = false,
              onSaveAllTabs = {},
              onOpenSession = {},
              onOpenTerminal = {},
            )
          }
        }
      }
    }
    compose.waitForIdle()
  }

  /** The touch box, which is what the platform reports for the button and what the display edge cuts. */
  private fun closeButton(title: String, verb: String = TAB): Rect =
    compose.onNodeWithContentDescription("$verb: $title").fetchSemanticsNode().touchBoundsInRoot

  private fun labelOf(title: String) = compose.onNode(hasText(title), useUnmergedTree = true)

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val TAB = "Close"
    const val KERNEL = "Shut Down Kernel"
    const val SHORT_A = "a.ipynb"
    const val SHORT_B = "b.py"
    const val LONG_NAME = "theme-baseline.ipynb"
  }
}
