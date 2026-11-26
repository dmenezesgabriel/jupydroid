package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceInterpreter
import com.jupydroid.app.domain.port.SpaceKernelRegistration
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One [SpaceKernelRegistration.register] as it was asked, recorded when its flow is collected - the flow is cold. */
data class KernelRegister(val spaceId: String, val interpreter: String)

/**
 * A [SpaceKernelRegistration] that registers nothing: every register is recorded and answered
 * from [eventsFor], the same shape as [FakeSpaceShell] - `AppContainer`'s default, so a screen
 * composes the register flow on a device with no Space. Interpreters are held per Space id, and a
 * register that finishes clean marks its interpreter registered under [kernelNameFor] - the
 * script's own fold, so the panel's chip reads the name a real register wrote.
 */
class FakeSpaceKernelRegistration(
  seeded: Map<String, List<SpaceInterpreter>> = mapOf(BASE_SPACE.id to SEEDED_INTERPRETERS),
) : SpaceKernelRegistration {
  private val bySpace = seeded.toMutableMap()

  /** Every register collected, oldest first - a `register` call alone records nothing. */
  val registers = mutableListOf<KernelRegister>()

  /** What each register emits, in order; the default is one line and a clean exit. */
  var eventsFor: suspend (KernelRegister) -> List<SpaceShellEvent> = { register ->
    listOf(SpaceShellEvent.Output("fake register ${register.interpreter}"), SpaceShellEvent.Finished(EXIT_OK))
  }

  /**
   * The spec name a register of [SpaceInterpreter.path] writes - the script's fold of an
   * absolute path (`/usr/bin/python3` to `usr-bin-python3`). A test registers an env-spelled
   * path, whose name the env's own rule picks, by handing its own.
   */
  var kernelNameFor: (String) -> String = { it.removePrefix("/").replace('/', '-') }

  override suspend fun interpreters(spaceId: String): List<SpaceInterpreter> = requireNotNull(bySpace[spaceId]) {
    "no Space with id '$spaceId'; expected one of ${bySpace.keys}"
  }

  override fun register(spaceId: String, interpreter: String): Flow<SpaceShellEvent> = flow {
    interpreters(spaceId)
    val register = KernelRegister(spaceId, interpreter)
    registers += register
    val events = eventsFor(register)
    events.forEach { emit(it) }
    if ((events.lastOrNull() as? SpaceShellEvent.Finished)?.exitCode == EXIT_OK) markRegistered(spaceId, interpreter)
  }

  private fun markRegistered(spaceId: String, interpreter: String) {
    bySpace[spaceId] = bySpace.getValue(spaceId).map {
      if (it.path == interpreter) it.copy(registeredAs = kernelNameFor(interpreter)) else it
    }
  }

  private companion object {
    const val EXIT_OK = 0

    /**
     * A Space's two own pythons, as `InRootPrograms` names them: the musl one the server runs
     * and the gnu payload's, so a device with no Space composes the same rows a real one lists.
     */
    val SEEDED_INTERPRETERS = listOf(
      SpaceInterpreter("/usr/bin/python3", isGnu = false),
      SpaceInterpreter("/opt/gnu/python/bin/python3.12", isGnu = true),
    )
  }
}
