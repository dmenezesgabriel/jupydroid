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

  private fun pathsIn(text: String): List<Pair<String, String>> =
    text.plainTextLinks(withPaths = true).map { text.substring(it.start, it.end) to it.destination }

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

  /** IPython 9's frame header (`IPython/core/tbtools.py` `_format_filename`, ipython 9.16.1). */
  @Test
  fun anIPythonFramesWorkspacePathIsLinkedWithItsLine() {
    assertEquals(listOf("~/mod.py:1" to "/home/jupydroid/mod.py"), pathsIn("File ~/mod.py:1, in f()"))
  }

  @Test
  fun aPythonFramesAbsolutePathIsLinkedWithItsLine() {
    assertEquals(
      listOf("/home/jupydroid/a/mod.py\", line 3" to "/home/jupydroid/a/mod.py"),
      pathsIn("  File \"/home/jupydroid/a/mod.py\", line 3, in f"),
    )
  }

  @Test
  fun aWarningsPathIsLinkedWithItsLineAndColumn() {
    assertEquals(
      listOf("/home/jupydroid/mod.py:3:7" to "/home/jupydroid/mod.py"),
      pathsIn("/home/jupydroid/mod.py:3:7: UserWarning: careful"),
    )
  }

  @Test
  fun aPathOutsideTheWorkspaceIsLeftAsText() {
    assertEquals(emptyList<Pair<String, String>>(), pathsIn("File /usr/lib/python3.12/json/__init__.py:346, in loads"))
  }

  /** JupyterLab's `autolink` looks for paths only between the web links it found first. */
  @Test
  fun aWebAddressWithAWorkspacePathInItStaysOneWebLink() {
    val text = "see https://x.org/home/jupydroid/a.py"

    assertEquals(
      listOf("https://x.org/home/jupydroid/a.py" to "https://x.org/home/jupydroid/a.py"),
      pathsIn(text),
    )
  }

  @Test
  fun aWebLinkAndAPathOnOneLineAreBothFound() {
    assertEquals(
      listOf("https://a.io" to "https://a.io", "~/b.py:2" to "/home/jupydroid/b.py"),
      pathsIn("https://a.io ~/b.py:2"),
    )
  }

  @Test
  fun withoutPathsOnlyWebAddressesAreLinked() {
    assertEquals(
      listOf(PlainTextLink(0, 12, "https://a.io")),
      "https://a.io ~/b.py:2".plainTextLinks(withPaths = false),
    )
  }

  /**
   * Bounded in thread CPU time, not wall time: a shared host's wall clock measures its neighbours.
   *
   * What a repeat costs depends on how far the JVM has compiled the regex loop, not on the input: the
   * same 50,000 repeats cost 0.9 to 2.5 s in fresh JVMs and 3.6 s in the full unit-test run, which
   * failed this test three times, and a run of them warm costs about 22 us a repeat against up to
   * 128 us cold. [RUNAWAY_REPEATS] of them cost at most 0.7 s at the cold rate, under a quarter of
   * [RUNAWAY_SCAN_BOUND_MS]. A scan that is not linear still misses the bound by orders of magnitude
   * over this many characters, which is what the test is for.
   */
  @Test
  fun aRunawayPrintIsScannedInLinearTime() {
    val text = "0123456789 http www. /usr/a/b.py:1 \", line ".repeat(RUNAWAY_REPEATS)
    val threads = ManagementFactory.getThreadMXBean()

    val before = threads.currentThreadCpuTime
    val links = text.plainTextLinks(withPaths = true)
    val spentMs = (threads.currentThreadCpuTime - before) / NANOS_PER_MS

    assertEquals(emptyList<PlainTextLink>(), links)
    assertTrue(
      "scanning took $spentMs ms of CPU; expected under $RUNAWAY_SCAN_BOUND_MS",
      spentMs < RUNAWAY_SCAN_BOUND_MS,
    )
  }

  private companion object {
    /** About 2 MB of text. */
    const val RUNAWAY_REPEATS = 5_000
    const val RUNAWAY_SCAN_BOUND_MS = 3_000L
    const val NANOS_PER_MS = 1_000_000L
  }
}
