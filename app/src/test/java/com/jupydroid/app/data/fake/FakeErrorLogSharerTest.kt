package com.jupydroid.app.data.fake

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeErrorLogSharerTest {
  @Test
  fun countsEveryShareItIsAskedFor() = runTest {
    val sharer = FakeErrorLogSharer()

    sharer.share()
    sharer.share()

    assertEquals(2, sharer.shareCount)
  }

  @Test
  fun hasSharedNothingUntilItIsAsked() {
    assertEquals(0, FakeErrorLogSharer().shareCount)
  }
}
