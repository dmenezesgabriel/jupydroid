package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceServers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Stub [SpaceServers] that runs no process; `JupyterSpaceServers` is the real one. */
class FakeSpaceServers : SpaceServers {
  private val runningIds = MutableStateFlow(emptySet<String>())

  override val running: StateFlow<Set<String>> = runningIds.asStateFlow()

  override suspend fun run(spaceId: String) = runningIds.update { it + spaceId }

  override suspend fun stop(spaceId: String) = runningIds.update { it - spaceId }

  override suspend fun stopAll() {
    runningIds.value = emptySet()
  }
}
