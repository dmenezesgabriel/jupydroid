package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** A Space's directory is named by its id under `spaces/`, which is how the scan derives the id back (ADR-0010). */
class SpacesDirectoryTest {
  private val spacesDir = File("/data/data/com.jupydroid.app/files/spaces")

  @Test
  fun aSpacesDirectoryIsItsIdUnderSpaces() {
    assertEquals(File(spacesDir, "space-1"), spaceDirIn(spacesDir, "space-1"))
  }
}
