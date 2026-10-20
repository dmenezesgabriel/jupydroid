package com.jupydroid.app.ui.shell

import androidx.compose.runtime.Composable
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The window holds one key row, and the screen being typed into says which.
 *
 * The case that needs a test is the handover: when one document replaces another, the incoming
 * screen's effect can publish before the outgoing one's is disposed, so a withdrawal has to take
 * away only its own row.
 */
class WindowKeyRowSlotTest {
  private val slot = WindowKeyRowSlot()

  private val cellRow: @Composable () -> Unit = {}
  private val terminalRow: @Composable () -> Unit = {}

  @Test
  fun withNothingPublishedThereIsNoRow() {
    assertNull(slot.row)
  }

  @Test
  fun aPublishedRowIsTheRow() {
    slot.publish(cellRow)

    assertSame(cellRow, slot.row)
  }

  @Test
  fun withdrawingTheRowLeavesNone() {
    slot.publish(cellRow)
    slot.withdraw(cellRow)

    assertNull(slot.row)
  }

  @Test
  fun anOutgoingScreenWithdrawingAfterTheIncomingOnePublishedLeavesTheIncomingRow() {
    slot.publish(cellRow)
    slot.publish(terminalRow)
    slot.withdraw(cellRow)

    assertSame(terminalRow, slot.row)
  }
}
