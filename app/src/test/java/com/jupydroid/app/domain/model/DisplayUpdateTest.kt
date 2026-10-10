package com.jupydroid.app.domain.model

import com.jupydroid.app.mimeBundleOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * How an `update_display_data` lands in one list of outputs: JupyterLab's `_onIOPub` sets each
 * output its id was shown as (jupyterlab@7f0e690 packages/outputarea/src/widget.ts).
 */
class DisplayUpdateTest {
  private fun frame(text: String, id: String?) =
    CellOutput.DisplayData(mimeBundleOf("text/plain" to text), displayId = id)

  @Test
  fun everyOutputCarryingTheIdIsReplacedWhole() {
    val width = Json.parseToJsonElement("""{"image/png":{"width":100}}""") as JsonObject
    val update = frame("'frame 3'", "d1").copy(metadata = width)

    val outputs = listOf(frame("'frame 0'", "d1"), frame("'frame 0'", "d1")).withDisplayReplaced(update)

    assertEquals(listOf(update, update), outputs)
  }

  @Test
  fun outputsOfAnotherIdOrNoneAreLeftAlone() {
    val others = listOf(
      frame("'other'", "d2"),
      frame("'plain'", null),
      CellOutput.Stream(StreamName.STDOUT, "d1"),
      frame("'frame 0'", "d1"),
    )

    val outputs = others.withDisplayReplaced(frame("'frame 1'", "d1"))

    assertEquals(others.dropLast(1) + frame("'frame 1'", "d1"), outputs)
  }

  /** A notebook-wide update visits every cell; one it does not touch keeps its list, not a copy. */
  @Test
  fun aListWithNoTargetIsReturnedAsItIs() {
    val untouched = listOf(frame("'other'", "d2"))

    assertSame(untouched, untouched.withDisplayReplaced(frame("'frame 1'", "d1")))
  }
}
