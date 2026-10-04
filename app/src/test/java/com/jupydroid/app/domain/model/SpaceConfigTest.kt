package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The two rules both [com.jupydroid.app.domain.port.SpaceRepository] implementations share.
 *
 * They live in the domain rather than inside the disk adapter because the fake needs the identical
 * answer - a fake that minted ids its own way would let a ViewModel test pass against a behaviour
 * no real Space has, which is the defect `FakeNotebookRepository`'s mtime stamping already records.
 */
class SpaceConfigTest {
  @Test
  fun theFirstCreatedSpaceIsSpaceOne() {
    assertEquals("space-1", emptyList<SpaceConfig>().nextSpaceId())
  }

  /** `base` carries no number, so it never takes part in the arithmetic. */
  @Test
  fun theBaseSpaceDoesNotCount() {
    assertEquals("space-1", listOf(spaceConfig(BASE_SPACE.id)).nextSpaceId())
  }

  /** The reference derives `maxContainerId` from the scan, so a gap is not filled. */
  @Test
  fun theNextIdIsOneAboveTheHighest_notTheFirstGap() {
    assertEquals("space-4", listOf(spaceConfig("space-1"), spaceConfig("space-3")).nextSpaceId())
  }

  /**
   * Deleting the highest hands its id back, because nothing is stored to say it was ever used.
   *
   * `ContainerManager.loadContainers` recomputes `maxContainerId` from the directories present
   * (`ContainerManager.java:44-64`), so this is the reference's own behaviour rather than an
   * oversight, and it is pinned here so a later "fix" has to argue with the reference.
   */
  @Test
  fun deletingTheHighestIdHandsItBack() {
    assertEquals("space-3", listOf(spaceConfig("space-1"), spaceConfig("space-2")).nextSpaceId())
  }

  /** A directory nobody here created is listed, but it cannot move the counter. */
  @Test
  fun aDirectoryWithNoNumberIsIgnoredByTheCounter() {
    assertEquals("space-1", listOf(spaceConfig("scratch"), spaceConfig("space-")).nextSpaceId())
  }

  @Test
  fun theBaseSpaceIsListedFirst() {
    val ordered = listOf(spaceConfig("space-1"), spaceConfig(BASE_SPACE.id)).inSpaceOrder()

    assertEquals(listOf(BASE_SPACE.id, "space-1"), ordered.map { it.id })
  }

  /** Numeric, not lexicographic: `sortedBy { it.id }` would put `space-10` before `space-2`. */
  @Test
  fun spacesAreOrderedByTheirNumberRatherThanTheirText() {
    val ordered = listOf(spaceConfig("space-10"), spaceConfig("space-2")).inSpaceOrder()

    assertEquals(listOf("space-2", "space-10"), ordered.map { it.id })
  }

  /** Anything unnumbered sorts after the numbered ones, by id, so the order is still total. */
  @Test
  fun anUnnumberedDirectorySortsAfterTheNumberedOnes() {
    val ordered = listOf(spaceConfig("scratch"), spaceConfig("space-2"), spaceConfig(BASE_SPACE.id)).inSpaceOrder()

    assertEquals(listOf(BASE_SPACE.id, "space-2", "scratch"), ordered.map { it.id })
  }

  private fun spaceConfig(id: String) = SpaceConfig(id = id, name = id, created = Instant.EPOCH)
}
