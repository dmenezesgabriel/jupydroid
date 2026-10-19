package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `OSC 8`, a hyperlink: `OSC 8 ; params ; URI ST` opens one and `OSC 8 ; ; ST` closes it, and every
 * cell written in between carries the URI (https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3cb5feda).
 * The parse is jediterm's `JediEmulator` OSC `case 8` (jediterm `377b76e6`); termux-app has none.
 */
class OperatingSystemHyperlinkTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun EmulatorFixture.linkAt(column: Int, row: Int): String? = emulator.screen.grid.hyperlinkAt(column, row)

  @Test
  fun theCellsWrittenWhileALinkIsOpenCarryItsUri() {
    val fixture = terminal(4, 2).enterString("$ESC]8;;https://a.b$ESC\\ab$ESC]8;;$ESC\\c")

    assertEquals("https://a.b", fixture.linkAt(column = 0, row = 0))
    assertEquals("https://a.b", fixture.linkAt(column = 1, row = 0))
    assertNull(fixture.linkAt(column = 2, row = 0))
  }

  @Test
  fun aBellTerminatesTheSequenceAsStringTerminatorDoes() {
    val fixture = terminal(4, 2).enterString("$ESC]8;;https://a.b${BEL}a$ESC]8;;${BEL}b")

    assertEquals("https://a.b", fixture.linkAt(column = 0, row = 0))
    assertNull(fixture.linkAt(column = 1, row = 0))
  }

  /** The params end at the first `;`, and the URI is everything after it, `;`s included. */
  @Test
  fun theParamsAreSkippedAndTheUriKeepsItsSemicolons() {
    val fixture = terminal(4, 2).enterString("$ESC]8;id=7;https://a.b/x;y${BEL}a")

    assertEquals("https://a.b/x;y", fixture.linkAt(column = 0, row = 0))
  }

  /** A link is not a graphic rendition, so resetting the renditions leaves it open. */
  @Test
  fun anSgrResetDoesNotCloseALink() {
    val fixture = terminal(4, 2).enterString("$ESC]8;;https://a.b${BEL}a$ESC[0mb")

    assertEquals("https://a.b", fixture.linkAt(column = 1, row = 0))
  }

  @Test
  fun aFullResetClosesALink() {
    val fixture = terminal(4, 2).enterString("$ESC]8;;https://a.b$BEL${ESC}ca")

    assertNull(fixture.linkAt(column = 0, row = 0))
  }

  @Test
  fun anOsc8WithNoSeparatorIsIgnored() {
    val fixture = terminal(4, 2).enterString("$ESC]8;;https://a.b$BEL$ESC]8${BEL}a")

    assertEquals("https://a.b", fixture.linkAt(column = 0, row = 0))
  }

  @Test
  fun anErasedCellIsNoLongerALink() {
    val fixture = terminal(4, 2).enterString("$ESC]8;;https://a.b${BEL}ab$ESC]8;;$BEL$ESC[2K")

    assertNull(fixture.linkAt(column = 0, row = 0))
  }

  /** ICH shifts the row's cells right through `TerminalRow.copyInterval`, and their links go with them. */
  @Test
  fun insertedBlanksPushALinkRight() {
    val fixture = terminal(6, 2).enterString("$ESC]8;;https://a.b${BEL}ab$ESC]8;;$BEL\r$ESC[2@")

    assertNull(fixture.linkAt(column = 0, row = 0))
    assertEquals("https://a.b", fixture.linkAt(column = 2, row = 0))
    assertEquals("https://a.b", fixture.linkAt(column = 3, row = 0))
  }

  @Test
  fun aLinkScrolledIntoTheScrollbackKeepsItsUri() {
    val fixture = terminal(4, 2).enterString("$ESC]8;;https://a.b${BEL}a$ESC]8;;$BEL\r\n\n\n")

    assertEquals("https://a.b", fixture.linkAt(column = 0, row = -2))
    assertNull(fixture.linkAt(column = 0, row = 1))
  }

  /** A narrower screen reflows the row through `GridReflow`, and each cell keeps its link. */
  @Test
  fun aReflowKeepsEachCellsLink() {
    val fixture = terminal(4, 3).enterString("ab$ESC]8;;https://a.b${BEL}cd$ESC]8;;$BEL").resize(2, 3)

    assertNull(fixture.linkAt(column = 1, row = 0))
    assertEquals("https://a.b", fixture.linkAt(column = 0, row = 1))
    assertEquals("https://a.b", fixture.linkAt(column = 1, row = 1))
  }

  private companion object {
    const val ESC = "\u001B"
    const val BEL = "\u0007"
  }
}
