package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.defaultSpaceConfig
import com.jupydroid.app.domain.model.inSpaceOrder
import com.jupydroid.app.domain.model.nextSpaceId
import com.jupydroid.app.domain.model.requireDeletableSpace
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * Spaces held in memory - the fake every port carries, for the reason every port has one: a
 * screen composes before its adapter is wired, and a device that cannot run a Space can still
 * draw the list of them. Nothing in [com.jupydroid.app.di.AppContainer] constructs the port yet;
 * this stands ready as its default once one does.
 *
 * Seeded with the base Space, because that is what an installed device holds and what
 * `SpaceRepositoryContract` asks of both implementations. Every rule it shares with
 * `DiskSpaceRepository` - the minted id, the order, the two refusals - is called rather than
 * copied: a fake that answered its own way would let a ViewModel test pass against a behaviour no
 * real Space has.
 */
class FakeSpaceRepository(initial: List<SpaceConfig> = listOf(defaultSpaceConfig(BASE_SPACE.id, Instant.EPOCH))) :
  SpaceRepository {
  private val spaces = MutableStateFlow(initial)

  override suspend fun spaces(): List<SpaceConfig> = spaces.value.inSpaceOrder()

  override suspend fun create(name: String): SpaceConfig {
    val created = SpaceConfig(id = spaces.value.nextSpaceId(), name = name, created = Instant.now())
    spaces.value = spaces.value + created
    return created
  }

  override suspend fun rename(id: String, name: String): SpaceConfig {
    val renamed = spaces.value.spaceWithId(id).copy(name = name)
    spaces.value = spaces.value.map { if (it.id == id) renamed else it }
    return renamed
  }

  override suspend fun delete(id: String) {
    requireDeletableSpace(id)
    spaces.value.spaceWithId(id)
    spaces.value = spaces.value.filterNot { it.id == id }
  }
}
