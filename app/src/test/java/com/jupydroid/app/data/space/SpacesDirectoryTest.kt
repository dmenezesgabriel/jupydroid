package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** A Space's directory is named by its id under `spaces/`, which is how the scan derives the id back (ADR-0010). */
class SpacesDirectoryTest {
  private val spacesDir = File("/data/data/com.jupydroid.app/files/spaces")

  /** The app's own `filesDir` and a backup agent's are one directory, so both find the Spaces there. */
  @Test
  fun theSpacesLiveUnderSpacesInFilesDir() {
    assertEquals(spacesDir, spacesDirIn(File("/data/data/com.jupydroid.app/files")))
  }

  @Test
  fun aSpacesDirectoryIsItsIdUnderSpaces() {
    assertEquals(File(spacesDir, "space-1"), spaceDirIn(spacesDir, "space-1"))
  }
}
