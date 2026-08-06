package com.jupydroid.app.ui.editor

import com.jupydroid.app.richtext.RichBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a folded cell says about itself. Kept out of the composable because the rule is a
 * judgement - JupyterLab's input placeholder takes `source.split('\n')[0]` and its output
 * placeholder takes the first *non-empty* line (`widget.ts:630`, `:1283-1289`).
 *
 * Both use the non-empty rule here, deliberately: taking line zero literally shows an empty
 * placeholder for a cell that happens to start with a blank line, which is the one case the
 * preview exists for.
 */
class CollapsedPreviewTest {
  @Test
  fun firstLine_namesTheCellByItsOpeningLine() {
    assertEquals("import numpy as np", firstNonEmptyLine("import numpy as np\nx = 1"))
  }

  @Test
  fun firstLine_skipsLeadingBlankLines() {
    assertEquals("x = 1", firstNonEmptyLine("\n   \nx = 1"))
  }

  @Test
  fun firstLine_ofNothingIsNothing() {
    assertEquals("", firstNonEmptyLine(""))
    assertEquals("", firstNonEmptyLine("\n \n"))
  }

  @Test
  fun outputsPreview_showsTheFirstLineOfTheFirstTextOutput() {
    val outputs = listOf(RenderedOutput.Text("42\n43", isError = false))

    assertEquals("42", outputs.collapsedPreview())
  }

  @Test
  fun outputsPreview_reachesPastAnImageToTheTextAfterIt() {
    val outputs = listOf(RenderedOutput.Image(ByteArray(1)), RenderedOutput.Text("after", isError = false))

    assertEquals("after", outputs.collapsedPreview())
  }

  /**
   * Nothing to preview is a real answer, and JupyterLab's too - `getOutputPlaceholderText`
   * returns undefined for an output with no string form, leaving just the ellipsis.
   */
  @Test
  fun outputsPreview_ofSomethingWithNoTextIsEmpty() {
    assertEquals("", listOf(RenderedOutput.Image(ByteArray(1))).collapsedPreview())
    assertEquals("", listOf(RenderedOutput.Document(listOf(RichBlock.ThematicBreak))).collapsedPreview())
    assertEquals("", emptyList<RenderedOutput>().collapsedPreview())
  }
}
