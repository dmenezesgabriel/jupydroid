package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SpaceTest {
  @Test
  fun spaceWithId_answersBaseWithTheBundledSpace() {
    assertSame(BASE_SPACE, spaceWithId("base"))
  }

  /** Every other Space is a copy of the bundled image, so it declares base's kernels under its own id. */
  @Test
  fun spaceWithId_answersAnotherIdWithBasesKernels() {
    assertEquals(Space(id = "space-1", name = "space-1", kernels = BASE_SPACE.kernels), spaceWithId("space-1"))
  }
}
