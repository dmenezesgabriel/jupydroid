package com.jupydroid.app.data.disk

import org.junit.Assert.assertEquals
import org.junit.Test

/** The name a checkpoint takes must be the server's, so the split is `os.path.splitext`'s. */
class SplitExtensionTest {
  @Test
  fun theExtensionIsFromTheLastDot() = assertEquals("a.tar" to ".gz", splitExtension("a.tar.gz"))

  @Test
  fun aNameWithNoDotHasNoExtension() = assertEquals("Makefile" to "", splitExtension("Makefile"))

  @Test
  fun leadingDotsArePartOfTheStem() = assertEquals(".bashrc" to "", splitExtension(".bashrc"))

  @Test
  fun aDotfileCanStillHaveAnExtension() = assertEquals(".env" to ".local", splitExtension(".env.local"))
}
