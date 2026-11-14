package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many whole glyphs fit in a measured container, kept a pure function so "one `resize` call
 * per real size change" is a plain JVM assertion rather than needing a device to exercise
 * `BoxWithConstraints`.
 */
class TerminalGridSizeTest {
  private val cell = Size(width = 10f, height = 20f)

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

  /**
   * A font advances 14.8 px where a whole-pixel cell says 15: 1000 px holds 67 columns drawn at the
   * pitch and only 66 counted at the rounded-up one, so the last column sits short of the edge.
   */
  @Test
  fun countsColumnsAtTheFontsFractionalAdvance() {
    val grid = terminalGridSize(containerPixels = IntSize(1000, 600), cell = Size(width = 14.8f, height = 20f))

    assertEquals(IntSize(width = 67, height = 30), grid)
  }
}
