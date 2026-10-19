package com.jupydroid.app.ui.terminal

import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [webLinkAt]: which printed web address, if any, a tap on a cell lands on - the address's whole
 * logical line is searched, so one a program wrapped past the right edge is still one link. And
 * [linkToOpenAt]: the cell's own OSC 8 link first, when it is a web one, then that printed address.
 */
class TerminalWebLinkTest {
  private val grid = TerminalGrid(columns = 12, totalRows = 8, screenRows = 4)

  @Test
  fun aCellInsideAPrintedAddressIsThatAddress() {
    write(row = 1, text = "a http://x.y")

    assertEquals("http://x.y", grid.webLinkAt(column = 5, row = 1))
  }

  @Test
  fun aCellBesideTheAddressOrBlankIsNoLink() {
    write(row = 0, text = "a http://x.y")

    assertNull(grid.webLinkAt(column = 0, row = 0))
    assertNull(grid.webLinkAt(column = 1, row = 0))
  }

  @Test
  fun theFullStopThatEndsASentenceIsNotPartOfTheAddress() {
    write(row = 0, text = "http://x.y.")

    assertEquals("http://x.y", grid.webLinkAt(column = 9, row = 0))
    assertNull(grid.webLinkAt(column = 10, row = 0))
  }

  @Test
  fun aWwwAddressOpensOverHttps() {
    write(row = 0, text = "www.x.org")

    assertEquals("https://www.x.org", grid.webLinkAt(column = 0, row = 0))
  }

  @Test
  fun anAddressWrappedOntoTheNextRowIsOneLinkFromEitherRow() {
    write(row = 0, text = "https://exam")
    grid.rowAt(0).lineWrap = true
    write(row = 1, text = "ple.org/a b")

    assertEquals("https://example.org/a", grid.webLinkAt(column = 3, row = 0))
    assertEquals("https://example.org/a", grid.webLinkAt(column = 8, row = 1))
    assertNull(grid.webLinkAt(column = 10, row = 1))
  }

  @Test
  fun anAddressScrolledIntoTheScrollbackIsStillALink() {
    write(row = 0, text = "http://x.y")
    grid.scrollDownOneLine(0, grid.screenRows, CellStyle.NORMAL)

    assertEquals("http://x.y", grid.webLinkAt(column = 2, row = -1))
  }

  @Test
  fun anOsc8LinkUnderTheTapIsOpenedWhereNoAddressIsPrinted() {
    write(row = 0, text = "see ")
    write(row = 0, text = "docs", from = 4, link = "https://a.b")

    assertEquals("https://a.b", grid.linkToOpenAt(column = 5, row = 0))
    assertNull(grid.linkToOpenAt(column = 1, row = 0))
  }

  @Test
  fun anOsc8LinkWinsOverTheAddressPrintedUnderIt() {
    write(row = 0, text = "http://x.y", link = "https://a.b")

    assertEquals("https://a.b", grid.linkToOpenAt(column = 2, row = 0))
  }

  /** `ALLOWED_LINK_SCHEMES` (jupyterlab@7f0e6906 packages/terminal/src/widget.ts): http and https only. */
  @Test
  fun anOsc8LinkOfAnotherSchemeIsNotOfferedAndThePrintedAddressIs() {
    write(row = 0, text = "http://x.y", link = "file:///etc/passwd")
    write(row = 1, text = "run", link = "javascript:alert(1)")

    assertEquals("http://x.y", grid.linkToOpenAt(column = 2, row = 0))
    assertNull(grid.linkToOpenAt(column = 0, row = 1))
  }

  @Test
  fun theSchemeIsMatchedWhateverItsCase() {
    write(row = 0, text = "docs", link = "HTTPS://a.b")

    assertEquals("HTTPS://a.b", grid.linkToOpenAt(column = 0, row = 0))
  }

  private fun write(row: Int, text: String, from: Int = 0, link: String? = null) =
    text.forEachIndexed { index, char -> grid.setChar(from + index, row, char.code, CellStyle.NORMAL, link) }
}
