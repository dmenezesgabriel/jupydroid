package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.registrableEnvironment
import com.jupydroid.app.domain.port.SpaceEnvironments

/**
 * Environments held in memory, per Space id - [com.jupydroid.app.di.AppContainer]'s default, so
 * Settings composes on a device with no Space. Seeded with base and no environment, as an
 * installed device holds. A registration is held as the environment's [PythonEnvironment.registeredAs].
 */
class FakeSpaceEnvironments(seeded: Map<String, List<PythonEnvironment>> = mapOf(BASE_SPACE.id to emptyList())) :
  SpaceEnvironments {
  private val bySpace = seeded.toMutableMap()

  override suspend fun environments(spaceId: String): List<PythonEnvironment> = requireNotNull(bySpace[spaceId]) {
    "no Space with id '$spaceId'; expected one of ${bySpace.keys}"
  }

  override suspend fun register(spaceId: String, path: String): String {
    val held = environments(spaceId)
    val holders = held.mapNotNull { one -> one.registeredAs?.let { it to one.path } }.toMap()
    val environment = registrableEnvironment(held, path, holders)
    bySpace[spaceId] = held.map { if (it == environment) it.copy(registeredAs = environment.kernelName) else it }
    return environment.kernelName
  }
}
