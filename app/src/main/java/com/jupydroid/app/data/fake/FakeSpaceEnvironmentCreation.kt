package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One [SpaceEnvironmentCreation.create] as it was asked, recorded when its flow is collected - the flow is cold. */
data class EnvironmentCreate(val spaceId: String, val project: String)

/**
 * A [SpaceEnvironmentCreation] that creates nothing: every create is recorded and answered from
 * [eventsFor], the same shape as [FakeSpaceShell] - `AppContainer`'s default, so a screen composes
 * the create flow on a device with no Space.
 */
class FakeSpaceEnvironmentCreation : SpaceEnvironmentCreation {
  /** Every create collected, oldest first - a `create` call alone records nothing. */
  val creates = mutableListOf<EnvironmentCreate>()

  /** What each create emits, in order; the default is one line and a clean exit. */
  var eventsFor: suspend (EnvironmentCreate) -> List<SpaceShellEvent> = { create ->
    listOf(SpaceShellEvent.Output("fake create ${create.project}"), SpaceShellEvent.Finished(EXIT_OK))
  }

  override fun create(spaceId: String, project: String): Flow<SpaceShellEvent> = flow {
    val create = EnvironmentCreate(spaceId, project)
    creates += create
    eventsFor(create).forEach { emit(it) }
  }

  private companion object {
    const val EXIT_OK = 0
  }
}
