package com.jupydroid.app

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the library gives the list pane at each width class, the numbers ADR-0141 accepts.
 *
 * The directive is the library's own function of a [WindowSizeClass], so a JVM test can state it with
 * no window: the same breakpoint set `currentWindowAdaptiveInfoV2()` reads, and the same call
 * `paneScaffoldDirective()` makes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PaneWidthByWindowClassTest {
  @Test
  fun anExpandedWindowGetsTwoPartitionsAndA360dpListPane() {
    val directive = directiveForWindowOf(widthDp = 900f)

    assertEquals(2, directive.maxHorizontalPartitions)
    assertEquals(360.dp, directive.defaultPanePreferredWidth)
  }

  @Test
  fun theLastExpandedWidthStillGetsA360dpListPane() {
    val directive = directiveForWindowOf(widthDp = 1199f)

    assertEquals(2, directive.maxHorizontalPartitions)
    assertEquals(360.dp, directive.defaultPanePreferredWidth)
  }

  @Test
  fun theTabletsLandscapeWidthIsLargeAndGetsA412dpListPane() {
    val directive = directiveForWindowOf(widthDp = 1280f)

    assertEquals(3, directive.maxHorizontalPartitions)
    assertEquals(412.dp, directive.defaultPanePreferredWidth)
  }

  @Test
  fun everyWindowWideEnoughForTwoPanesLeavesA24dpSpacerBetweenThem() {
    val spacers: List<Dp> = listOf(900f, 1199f, 1280f).map { directiveForWindowOf(it).horizontalPartitionSpacerSize }

    assertEquals(listOf(24.dp, 24.dp, 24.dp), spacers)
  }

  private fun directiveForWindowOf(widthDp: Float): PaneScaffoldDirective {
    val sizeClass = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, HEIGHT_DP)
    return calculatePaneScaffoldDirective(WindowAdaptiveInfo(sizeClass, Posture()))
  }

  private companion object {
    /** Tall enough to be Medium or Expanded in height; the pane widths do not read it. */
    const val HEIGHT_DP = 800f
  }
}
