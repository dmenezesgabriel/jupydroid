package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPageHandleTest {
  private val evaluated = mutableListOf<String>()
  private val page = EditorPageHandle { evaluated += it }

  /**
   * Regression: Compose pushes the source on the first composition, before the WebView has
   * loaded. Calling then died with "Cannot read properties of undefined (reading
   * 'setSource')" and left a blank editor with nothing on screen to explain it.
   */
  @Test
  fun showSource_saysNothingUntilThePageIsReady() {
    page.showSource("2 + 2")

    assertEquals(emptyList<String>(), evaluated)
  }

  @Test
  fun onReady_sendsTheThemeAndTheSourceThatWereWaiting() {
    page.applyTheme(mapOf("background" to "#101418"))
    page.showSource("2 + 2")

    page.onReady()

    assertEquals(2, evaluated.size)
    assertTrue("theme first, so the editor never paints unstyled: $evaluated", evaluated[0].contains("setTheme"))
    assertTrue(evaluated[1].contains("setSource"))
  }

  /** Only the latest matters: earlier sources were never seen, so replaying them is noise. */
  @Test
  fun onReady_sendsOnlyTheMostRecentSource() {
    page.showSource("first")
    page.showSource("second")

    page.onReady()

    assertEquals(1, evaluated.size)
    assertTrue("expected the newest source, got ${evaluated.single()}", evaluated.single().contains("second"))
  }

  @Test
  fun showSource_goesStraightThroughOnceThePageIsReady() {
    page.onReady()

    page.showSource("2 + 2")

    assertEquals(1, evaluated.size)
  }
}
