package com.jupydroid.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A workbench part's border: one line, on the side facing the next part, over the part's own paint.
 *
 * **The mechanism is the reference's own.** VS Code has each part draw its own edge rather than
 * putting a separator between them - `sidebarPart.ts:175-180` sets `borderRightWidth = '1px'` on
 * the side bar's container, and `activitybarpart.css:30-34` is a `border-right` on the activity
 * bar's. JupyterLab budgets the same line into the tab strip's width,
 * `calc(var(--jp-private-sidebar-tab-width) + var(--jp-border-width))`
 * (`packages/application/style/sidepanel.css:41-44`).
 *
 * A pixel read rather than a node assertion, because what is claimed here is a *colour* and a
 * semantics tree cannot carry one - the same reason `CellEditorBoxTest` reads its own edge and
 * `read_chrome_parts.py` exists for the device half.
 */
@RunWith(AndroidJUnit4::class)
class PartBorderTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theBorderIsDrawnOnTheTrailingEdge() {
    compose.setContent { BorderedPart() }

    val pixels = compose.onNodeWithTag(PART).captureToImage().toPixelMap()
    assertEquals(BORDER, pixels[pixels.width - 1, pixels.height / 2])
  }

  /**
   * **Over the part, not under it.** `drawBehind` would put the line beneath whatever the part
   * paints - a `NavigationRail`'s `containerColor`, a `Scaffold`'s surface - and every one of the
   * app's parts paints its own background, so the border would never be seen.
   */
  @Test
  fun theBorderSurvivesThePartPaintingItsOwnBackground() {
    compose.setContent { BorderedPart() }

    val pixels = compose.onNodeWithTag(PART).captureToImage().toPixelMap()
    assertEquals(BORDER, pixels[pixels.width - 1, 0])
  }

  /** One line and not a wash: the part keeps its own colour everywhere but its own edge. */
  @Test
  fun theRestOfThePartIsUntouched() {
    compose.setContent { BorderedPart() }

    val pixels = compose.onNodeWithTag(PART).captureToImage().toPixelMap()
    assertEquals(FILL, pixels[0, pixels.height / 2])
    assertEquals(FILL, pixels[pixels.width / 2, pixels.height / 2])
  }

  /**
   * **Trailing, which in a right-to-left locale is the left edge.**
   *
   * The reference decides the same thing rather than hardcoding a side: `sidebarPart.ts:176`
   * branches on `isPositionLeft` and paints `borderLeftWidth` when the bar is on the other side.
   * Here the shell's own `Row` mirrors, so the side panel moves and its border has to move with it
   * - and the icon set is already chosen with this locale in mind (`SidePanelPresentation.kt`).
   */
  @Test
  fun aRightToLeftLayoutDrawsTheBorderOnTheOtherSide() {
    compose.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { BorderedPart() }
    }

    val pixels = compose.onNodeWithTag(PART).captureToImage().toPixelMap()
    assertEquals(BORDER, pixels[0, pixels.height / 2])
    assertEquals(FILL, pixels[pixels.width - 1, pixels.height / 2])
  }

  @Composable
  private fun BorderedPart() {
    Box(
      Modifier.testTag(PART).fillMaxSize().trailingBorder(BORDER).background(FILL),
    )
  }

  private companion object {
    const val PART = "part"

    /** Distinct from [BORDER] in every channel, so a sampled pixel can only be one of the two. */
    val FILL = Color(0xFF191A1B)
    val BORDER = Color(0xFF2A2B2C)
  }
}
