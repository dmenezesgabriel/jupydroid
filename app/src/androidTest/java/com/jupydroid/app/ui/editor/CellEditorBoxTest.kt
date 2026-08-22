package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.DarkEditorPalette
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The panel a cell's source is typed into, and the two things about it no JVM test can answer:
 * what colour is actually painted at its edge, and whether drawing that edge moved anything.
 *
 * The reference draws the border **unconditionally** and re-colours it for the one cell being
 * edited (`packages/cells/style/inputarea.css:18-26`,
 * `packages/notebook/style/base.css:160-164`), so both states are asserted rather than only the
 * interesting one - a border that was brand blue always would pass a test that checked edit mode
 * alone.
 *
 * Colours are compared by *nearest match* rather than by equality, and that is the shape of the
 * measurement rather than a loosened assertion: the edge pixel of an antialiased rounded-rect
 * stroke is a blend of the stroke with what is behind it, so exact equality would be asserting
 * the renderer's blending and not the palette.
 */
@RunWith(AndroidJUnit4::class)
class CellEditorBoxTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theRestingBorderIsDrawnInTheEditorBorderColour() {
    compose.setContent { JupyterDroidTheme(darkTheme = true) { BoxedField(editing = false) } }

    assertEquals(DarkEditorPalette.cellEditorBorder, nearestPaletteColourAtLeftEdge())
  }

  @Test
  fun theBorderTurnsBrandBlueForTheCellBeingEdited() {
    compose.setContent { JupyterDroidTheme(darkTheme = true) { BoxedField(editing = true) } }

    assertEquals(DarkEditorPalette.stripeSelected, nearestPaletteColourAtLeftEdge())
  }

  /**
   * **The border costs no space**, which is the thing this row could have broken silently.
   * `Modifier.border` strokes inside the layout bounds, so the content it wraps sits exactly where
   * `.background().padding()` alone put it - the shape all three fields had before P2m-2. Asserted
   * against that shape rather than against a number, because the number is whatever `Spacing.sm`
   * is and the claim is that nothing moved.
   *
   * The cell gutter's x is P2n's whole subject; pre-breaking it here would be found late.
   */
  @Test
  fun drawingTheBorderDoesNotMoveTheContentInsideIt() {
    var bordered by mutableStateOf(false)
    compose.setContent {
      JupyterDroidTheme(darkTheme = true) {
        if (bordered) {
          BoxedField(editing = true)
        } else {
          Box(
            Modifier
              .fillMaxWidth()
              .background(Color.Black, MaterialTheme.shapes.small)
              .padding(Spacing.sm),
          ) { Box(Modifier.testTag(CONTENT).fillMaxWidth().height(FIELD_HEIGHT)) }
        }
      }
    }
    val plain = compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().left

    bordered = true
    compose.waitForIdle()

    assertEquals(plain, compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().left)
  }

  /** The edge of the box, sampled at mid-height where the rounded corners are not in the way. */
  private fun nearestPaletteColourAtLeftEdge(): Color {
    val pixels = compose.onNodeWithTag(BOX).captureToImage().toPixelMap()
    val sampled = pixels[0, pixels.height / 2]
    return CANDIDATES.minByOrNull { channelDistance(it, sampled) }!!
  }

  @Composable
  private fun BoxedField(editing: Boolean) {
    Box(Modifier.testTag(BOX).fillMaxWidth().cellEditorBox(editing)) {
      Box(Modifier.testTag(CONTENT).fillMaxWidth().height(FIELD_HEIGHT))
    }
  }

  private companion object {
    const val BOX = "editor-box"
    const val CONTENT = "editor-content"

    /** Tall enough that the mid-height sample is clear of both rounded corners. */
    val FIELD_HEIGHT = 64.dp

    /** The three colours the edge could plausibly be: either border, or no border at all. */
    val CANDIDATES = listOf(
      DarkEditorPalette.cellEditorBorder,
      DarkEditorPalette.stripeSelected,
      DarkEditorPalette.cellBackground,
    )

    fun channelDistance(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
  }
}
