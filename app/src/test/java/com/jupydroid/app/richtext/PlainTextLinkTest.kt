package com.jupydroid.app.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

/**
 * Which web addresses printed text holds: JupyterLab's `webLinkRegex`, narrowed to what a tap follows
 * (jupyterlab@7f0e690 packages/rendermime/src/renderers.ts).
 */
class PlainTextLinkTest {
  private fun linkedIn(text: String): List<String> = text.plainTextWebLinks().map { text.substring(it.start, it.end) }

  @Test
  fun aSentencesFullStopIsNotPartOfItsLink() {
    assertEquals(listOf("https://jupyter.org"), linkedIn("see https://jupyter.org."))
  }

  @Test
  fun aClosingBracketIsNotPartOfItsLink() {
    assertEquals(listOf("https://a.b/c"), linkedIn("(https://a.b/c)"))
  }

  @Test
  fun aQuoteEndsALink() {
    assertEquals(listOf("http://a.b/c"), linkedIn("url='http://a.b/c'"))
    assertEquals(listOf("http://a.b/c"), linkedIn("\"http://a.b/c\" here"))
  }

  @Test
  fun anAngleBracketedAddressDropsItsBrackets() {
    assertEquals(listOf("https://jupyter.org"), linkedIn("<https://jupyter.org>"))
  }

  @Test
  fun aQueryAndAFragmentStayInTheLink() {
    assertEquals(listOf("https://x.org/a?b=1&c=2#d"), linkedIn("go https://x.org/a?b=1&c=2#d now"))
  }

  /** `WebLinker.createAnchor` prefixes `https://` to a `www.` address; the text drawn is what was printed. */
  @Test
  fun aWwwAddressGoesOverHttps() {
    val link = "visit www.python.org".plainTextWebLinks().single()

    assertEquals("www.python.org", "visit www.python.org".substring(link.start, link.end))
    assertEquals("https://www.python.org", link.destination)
  }

  @Test
  fun aSchemeIsMatchedInAnyCase() {
    assertEquals(listOf("HTTPS://X.ORG"), linkedIn("HTTPS://X.ORG"))
  }

  /** Nothing follows these (`isWebLinkDestination`), and a link that does nothing reads as broken. */
  @Test
  fun aSchemeNoTapFollowsIsLeftAsText() {
    assertEquals(emptyList<String>(), linkedIn("ftp://x.org/f data:text/plain,hi file:///etc/hosts"))
  }

  @Test
  fun everyLinkOnALineIsFound() {
    assertEquals(listOf("http://a.io", "https://b.io/x"), linkedIn("http://a.io and https://b.io/x"))
  }

  @Test
  fun aLinkEndsAtTheLineItIsOn() {
    assertEquals(listOf("https://a.io"), linkedIn("https://a.io\nnext"))
  }

  @Test
  fun textWithNoAddressHasNoLink() {
    assertEquals(emptyList<String>(), linkedIn("ValueError: invalid literal for int() with base 10: 'x'"))
  }

  /** Bounded in thread CPU time, not wall time: a shared host's wall clock measures its neighbours. */
  @Test
  fun aRunawayPrintIsScannedInLinearTime() {
    val text = "0123456789 http www. ".repeat(RUNAWAY_REPEATS)
    val threads = ManagementFactory.getThreadMXBean()

    val before = threads.currentThreadCpuTime
    val links = text.plainTextWebLinks()
    val spentMs = (threads.currentThreadCpuTime - before) / NANOS_PER_MS

    assertEquals(emptyList<PlainTextLink>(), links)
    assertTrue(
      "scanning took $spentMs ms of CPU; expected under $RUNAWAY_SCAN_BOUND_MS",
      spentMs < RUNAWAY_SCAN_BOUND_MS,
    )
  }

  private companion object {
    /** About 2 MB of text. */
    const val RUNAWAY_REPEATS = 100_000
    const val RUNAWAY_SCAN_BOUND_MS = 3_000L
    const val NANOS_PER_MS = 1_000_000L
  }
}
