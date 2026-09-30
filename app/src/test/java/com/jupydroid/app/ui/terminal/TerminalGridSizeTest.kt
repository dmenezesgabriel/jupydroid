package com.jupydroid.app.ui.terminal

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many whole glyphs fit in a measured container, kept a pure function so "one `resize` call
 * per real size change" is a plain JVM assertion rather than needing a device to exercise
 * `BoxWithConstraints`.
 */
class TerminalGridSizeTest {
  private val cell = IntSize(width = 10, height = 20)

  @Test
  fun fitsWholeCellsExactly() {
    val grid = terminalGridSize(containerPixels = IntSize(800, 600), cell = cell)

    assertEquals(IntSize(width = 80, height = 30), grid)
  }

  /** 805 / 10 and 615 / 20 both leave a remainder - a partial cell draws nothing, so it is dropped. */
  @Test
  fun truncatesARemainderRatherThanRoundingUp() {
    val grid = terminalGridSize(containerPixels = IntSize(805, 615), cell = cell)

    assertEquals(IntSize(width = 80, height = 30), grid)
  }

  /** `TerminalEmulator.resize`'s own `< 2` floor - a container too small to hold it still gets one. */
  @Test
  fun clampsToTheEmulatorsTwoByTwoFloor() {
    val grid = terminalGridSize(containerPixels = IntSize(5, 5), cell = cell)

    assertEquals(IntSize(width = 2, height = 2), grid)
  }

  @Test
  fun clampsOnlyTheDimensionThatIsTooSmall() {
    val grid = terminalGridSize(containerPixels = IntSize(800, 5), cell = cell)

    assertEquals(IntSize(width = 80, height = 2), grid)
  }
}
