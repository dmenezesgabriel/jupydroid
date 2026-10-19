package com.jupydroid.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/** What date a restored file is given, from the mtime the platform's tar reader hands the agent. */
class SpaceHomeBackupRestoreTest {
  /**
   * A tar header stores seconds, and `File.setLastModified` takes milliseconds: passed through
   * unconverted, 2025's 1,760,000,000 s lands 20 days after the epoch, on 1970-01-21.
   */
  @Test
  fun aTarMtimeInSecondsBecomesMilliseconds() {
    assertEquals(1_760_000_000_000L, restoredModifiedMillis(1_760_000_000L))
  }
}
