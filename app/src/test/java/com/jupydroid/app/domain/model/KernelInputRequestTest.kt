package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The line an answer leaves in the cell - JupyterLab's echo, so a notebook answered here reads the
 * same there (jupyterlab@7f0e690 packages/outputarea/src/widget.ts, `Stdin.handleEvent`).
 */
class KernelInputRequestTest {
  @Test
  fun anAnswerEchoesAsThePromptASpaceAndTheValueOnStdin() {
    val request = KernelInputRequest(prompt = "Name?", password = false, requestHeader = "{}")

    assertEquals(CellOutput.Stream(StreamName.STDIN, "Name? Ada\n"), request.echoOf("Ada"))
  }

  /** A password never reaches the notebook: the echo carries JupyterLab's mask instead. */
  @Test
  fun aPasswordAnswerEchoesAsTheMaskNotTheValue() {
    val request = KernelInputRequest(prompt = "Secret?", password = true, requestHeader = "{}")

    assertEquals(CellOutput.Stream(StreamName.STDIN, "Secret? ········\n"), request.echoOf("abc"))
  }
}
