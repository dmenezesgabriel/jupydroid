package com.jupydroid.app.ui.explorer

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How often the browser re-reads the directory it is showing.
 *
 * The number is the reference's, not ours: `DEFAULT_REFRESH_INTERVAL = 10000`
 * (`packages/filebrowser/src/model.ts:26`), which `FileBrowserModel` gives its `Poll`. Pinned here
 * so that changing it has to be an argument with the reference rather than a quiet edit - the same
 * reason `MAX_RECENTS` is pinned to `maxNumberRecents`.
 *
 * What the interval *does* is a lifecycle question and cannot be asked here; `ExplorerScreenTest`
 * drives it on a device, by writing a file behind the screen's back and touching nothing.
 */
class ExplorerRefreshIntervalTest {
  @Test
  fun theIntervalIsTheReferenceDefault() {
    assertEquals(10.seconds, EXPLORER_REFRESH_INTERVAL)
  }
}
