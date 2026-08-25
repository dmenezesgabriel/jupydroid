package com.jupydroid.app.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.OfflineBolt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which glyph each kernel state draws - the pin `CellRunIconTest` is the pattern for.
 *
 * The glyphs are values rather than inline arguments so this has something to hold: a departure
 * back to a filled disc is otherwise a one-token edit no gate would see.
 */
class KernelStatusGlyphTest {
  /**
   * `circleEmptyIcon`, which is `circle-empty.svg` - a stroked ring, and the reference's own mark
   * for a kernel with nothing to do (`packages/apputils/src/toolbar/widget.tsx:229`).
   */
  @Test
  fun anIdleKernelIsAnEmptyCircle() {
    assertEquals(Icons.Outlined.Circle, KernelStatus.IDLE.glyph())
  }

  /**
   * **The reference's glyph at this row's weight, and the departure is the weight alone.**
   * `offline-bolt.svg` is Material's *filled* `offline_bolt` verbatim, a solid disc with the bolt
   * cut out of it - and every toolbar icon there, this one included, is `jp-icon3`, so the
   * reference makes no tonal distinction either
   * (`packages/ui-components/style/icons.css:23`, `theme-light-extension/style/variables.css:231`).
   *
   * It is still wrong here, and the reason is which state *rests*. JupyterLab opens a session with
   * the document, so it is practically never in this one; this app starts a kernel lazily, so this
   * is what every notebook shows until something is run. Drawn filled beside four outlined glyphs
   * it was the loudest thing in the row - measured off the frame, not guessed - and a resting state
   * that reads as an alarm is the misread P2v-1 retired the filled play for. Outlined keeps the
   * reference's glyph and changes only its weight. `docs/DECISIONS.md` carries it.
   */
  @Test
  fun aNotebookWithNoKernelDrawsAnOutlinedBolt() {
    assertEquals(Icons.Outlined.OfflineBolt, KernelStatus.NO_KERNEL.glyph())
  }

  /** Guards the exact regression above, since identity makes the pair comparable. */
  @Test
  fun theFilledBoltIsNotWhatTheBarDraws() {
    assertNotEquals(Icons.Filled.OfflineBolt, KernelStatus.NO_KERNEL.glyph())
  }

  /**
   * **Solid while there is work**, which is `circleIcon` - the glyph the reference's `KernelStatus`
   * item drew for busy, starting and restarting alike
   * (`packages/apputils/src/toolbar/widget.tsx:222-232`), and which `ExecutionIndicator` still
   * draws for starting and restarting (`packages/notebook/src/executionindicator.tsx:140-151`).
   *
   * The three are one glyph there too; what tells them apart is the description this control also
   * carries. See [KernelStatus.glyph] for why the *busy* half follows the retired item rather than
   * `ExecutionIndicator`'s progress meter, whose polarity is the inverse.
   */
  @Test
  fun theStatesThatAreWorkingAreSolid() {
    assertEquals(Icons.Filled.Circle, KernelStatus.BUSY.glyph())
    assertEquals(Icons.Filled.Circle, KernelStatus.STARTING.glyph())
    assertEquals(Icons.Filled.Circle, KernelStatus.RESTARTING.glyph())
  }

  /**
   * The rule the three cases above state, as one assertion: **active is solid, resting is
   * outlined.** Written over the whole enum so a state added later cannot quietly rest as a solid
   * mark, which is the thing the eye reads first and the description second.
   */
  @Test
  fun everyRestingStateIsOutlinedAndEveryActiveOneIsNot() {
    val resting = setOf(KernelStatus.IDLE, KernelStatus.NO_KERNEL)
    val outlined = setOf(Icons.Outlined.Circle, Icons.Outlined.OfflineBolt)

    KernelStatus.entries.forEach { status ->
      val isOutlined = status.glyph() in outlined
      assertEquals("$status draws ${status.glyph().name}", status in resting, isOutlined)
    }
  }
}
