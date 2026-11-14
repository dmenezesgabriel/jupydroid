package com.jupydroid.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG
import com.jupydroid.app.ui.terminal.TerminalPaneScreen
import com.jupydroid.app.ui.terminal.TerminalPaneViewModel
import com.jupydroid.app.ui.terminal.terminalCellMetrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `TerminalCanvasTest.theGridShrinksWhenTheImeInsetOpens` proves the keyboard shrinks
 * `TerminalCanvas` when it is composed alone. It cannot prove *how much* - composed inside
 * `TerminalPaneScreen`'s real `Scaffold`, next to `TerminalExtraKeysRow` (which already carries
 * its own `imePadding` as the bottom bar), the same `imePadding` on `TerminalCanvas` shrank an
 * already-correctly-sized content area a second time.
 *
 * `terminalCellMetrics()` is called as a plain expression - not a second `fillMaxSize` sibling -
 * specifically so `TerminalPaneScreen` stays the composition's only layout-emitting root, matching
 * how the app actually composes it; two `fillMaxSize` roots were tried first and starved the
 * screen down to `MINIMUM_CELLS`, a test-authoring mistake this comment exists to not repeat.
 */
@RunWith(AndroidJUnit4::class)
class TerminalPaneScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun theTerminalShrinksByRoughlyOneKeyboardsWorthOfRowsNotTwo() {
    val gateway = FakeTerminalGateway()
    val session = runBlocking { gateway.open() }
    val viewModel = TerminalPaneViewModel(session, gateway)
    var cell by mutableStateOf<Size?>(null)

    // Pinned rather than left to whatever the physical device was last rotated to - the tablet's
    // own landscape default has already cost `terminal-resize`/`terminal-font-size`
    // a false start apiece, and here it swung the "without ime" baseline between
    // 12 and 29 rows depending on which way an earlier test run happened to leave the device.
    compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    compose.waitForIdle()

    compose.setContent {
      JupyterDroidTheme {
        cell = terminalCellMetrics()
        TerminalPaneScreen(session = session, terminalGateway = gateway, viewModel = viewModel)
      }
    }
    val rowsWithoutIme = settledRows(viewModel)

    compose.runOnUiThread {
      val withIme = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, IME_INSET_PIXELS))
        .setVisible(WindowInsetsCompat.Type.ime(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, withIme)
    }
    val rowsWithIme = settledRows(viewModel)

    val cellHeight = requireNotNull(cell) { "terminalCellMetrics was never composed" }.height.toInt()
    val shrunkByRows = rowsWithoutIme - rowsWithIme
    // `terminalGridSize`'s own floor division, not a hand-derived ratio - the same arithmetic
    // `TerminalCanvas` uses to turn pixels into rows. Measured on device rather than treated as
    // exact: a single correct `imePadding` subtraction lands close to but not always exactly at
    // this floor-divided figure (the finding about the double-counted IME inset has
    // the numbers), so the tolerance below is asymmetric on purpose - tight above, where a doubled
    // inset would show up, looser below, where only rounding lives.
    val expectedRows = IME_INSET_PIXELS / cellHeight
    assertTrue(
      "the terminal shrank by $shrunkByRows rows for a $IME_INSET_PIXELS px keyboard " +
        "($cellHeight px per row); expected roughly $expectedRows (one keyboard's worth), " +
        "not roughly ${2 * expectedRows} (a doubled inset)",
      shrunkByRows in (expectedRows - LOWER_SLACK_ROWS)..(expectedRows + UPPER_SLACK_ROWS),
    )
  }

  /**
   * The ViewModel outlives the activity a theme change relaunches (ADR-0092), so the palette it
   * was built with is not the one the terminal may draw with afterwards. Built light and passed
   * in, as a retained one is; the theme then turns dark, and the empty grid's centre has to follow.
   */
  @Test
  fun aRetainedTerminalRedrawsInTheNewThemesPalette() {
    val gateway = FakeTerminalGateway()
    val session = runBlocking { gateway.open() }
    val viewModel = TerminalPaneViewModel(session, gateway, palette = TerminalPalette.VS_CODE_LIGHT)
    var dark by mutableStateOf(false)
    compose.setContent {
      JupyterDroidTheme(darkTheme = dark) {
        TerminalPaneScreen(session = session, terminalGateway = gateway, viewModel = viewModel)
      }
    }
    compose.waitForIdle()

    dark = true
    compose.waitForIdle()

    // The terminal's own top edge, not the screen's centre: the focused field raises the keyboard
    // on a device, and the extra-keys row it pushes up sits there instead.
    val terminal = compose.onNodeWithTag(TERMINAL_INPUT_TAG).getBoundsInRoot()
    val x = with(compose.density) { ((terminal.left + terminal.right) / 2).roundToPx() }
    val y = with(compose.density) { terminal.top.roundToPx() } + 2
    val pixels = compose.onRoot().captureToImage().toPixelMap()
    assertEquals(TerminalPalette.VS_CODE_DARK.at(CellStyle.COLOR_INDEX_BACKGROUND), pixels[x, y].toArgb())
  }

  /**
   * A resize is a multi-hop cascade, not one relayout - the ime inset change reaches
   * `TerminalCanvas`'s `BoxWithConstraints`, whose `LaunchedEffect` reports it through
   * `onGridSizeChanged` into `TerminalPaneViewModel.resize`, which mutates the emulator and bumps
   * `redraw`, which recomposes `TerminalCanvas` again inside `key(state.redraw)`. A single
   * `waitForIdle()` after dispatching the inset caught this mid-cascade often enough to be flaky
   * (`shrank by -1 rows` on two separate runs) - waiting until two consecutive idle passes agree
   * is what actually waits for the cascade to finish, not for one arbitrary hop in it.
   */
  private fun settledRows(viewModel: TerminalPaneViewModel): Int {
    var previous = viewModel.emulator.screen.rows
    repeat(MAX_SETTLE_PASSES) {
      compose.waitForIdle()
      val current = viewModel.emulator.screen.rows
      if (current == previous) return current
      previous = current
    }
    return previous
  }

  private companion object {
    /** Same value `TerminalCanvasTest` already uses for its own synthetic keyboard. */
    const val IME_INSET_PIXELS = 600
    const val LOWER_SLACK_ROWS = 5
    const val UPPER_SLACK_ROWS = 3
    const val MAX_SETTLE_PASSES = 10
  }
}
