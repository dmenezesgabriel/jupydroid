package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** How far an image is shrunk while it decodes, so the bitmap stays drawable on every release the app runs on. */
class ImageSampleSizeTest {
  @Test
  fun anImageThatFitsDecodesWhole() {
    assertEquals(1, imageSampleSize(width = 1200, height = 800))
  }

  /** 3620² pixels at the worst case's 8 bytes is just under 100 MiB. */
  @Test
  fun anImageJustUnderTheLimitDecodesWhole() {
    assertEquals(1, imageSampleSize(width = 3620, height = 3620))
  }

  @Test
  fun anImageJustOverTheLimitDecodesAtHalfItsSides() {
    assertEquals(2, imageSampleSize(width = 3630, height = 3630))
  }

  @Test
  fun anImageFarOverTheLimitHalvesUntilItFits() {
    assertEquals(8, imageSampleSize(width = 20_000, height = 20_000))
  }
}
