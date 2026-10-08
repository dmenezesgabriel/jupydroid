package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.port.SpaceEnvironments

/**
 * Environments held in memory, per Space id - [com.jupydroid.app.di.AppContainer]'s default, so
 * Settings composes on a device with no Space. Seeded with base and no environment, as an
 * installed device holds.
 */
class FakeSpaceEnvironments(
  private val bySpace: Map<String, List<PythonEnvironment>> = mapOf(BASE_SPACE.id to emptyList()),
) : SpaceEnvironments {
  override suspend fun environments(spaceId: String): List<PythonEnvironment> = requireNotNull(bySpace[spaceId]) {
    "no Space with id '$spaceId'; expected one of ${bySpace.keys}"
  }
}
