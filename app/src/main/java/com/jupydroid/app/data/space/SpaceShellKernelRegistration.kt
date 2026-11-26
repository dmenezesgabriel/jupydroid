package com.jupydroid.app.data.space

import com.jupydroid.app.data.disk.kernelNameByInterpreter
import com.jupydroid.app.domain.model.SpaceInterpreter
import com.jupydroid.app.domain.port.SpaceKernelRegistration
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * [SpaceKernelRegistration] over [SpaceShell]: the register is the Space's own `jupydroid-env`
 * helper - installed at `/usr/local/bin` by `installOnStartOf`
 * (`AndroidSpaceStartInstall.kt`), so the terminal runs the same bytes by name - asked to make
 * the interpreter a kernelspec. This adapter owns the command's shape - the helper's name, the
 * register verb, the quoting and the bound; the script owns what registering is.
 *
 * [interpreters] reads the Space's disk instead: the catalogue's `argv[0]` names which spec runs
 * which interpreter, the same answer [kernelNameByInterpreter] gives an environment's row, so a
 * stopped Space lists its interpreters without a run.
 */
class SpaceShellKernelRegistration(
  private val spaceShell: SpaceShell,
  private val spacesDir: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpaceKernelRegistration {
  override suspend fun interpreters(spaceId: String): List<SpaceInterpreter> = withContext(ioDispatcher) {
    val registered = kernelNameByInterpreter(spaceDirsOf(spaceId))
    listOf(
      SpaceInterpreter(SPACE_INTERPRETER, isGnu = false, registeredAs = registered[SPACE_INTERPRETER]),
      SpaceInterpreter(GNU_INTERPRETER, isGnu = true, registeredAs = registered[GNU_INTERPRETER]),
    )
  }

  override fun register(spaceId: String, interpreter: String): Flow<SpaceShellEvent> =
    spaceShell.run(spaceId, "jupydroid-env register ${interpreter.shellQuoted()}", REGISTER_TIMEOUT)

  private fun spaceDirsOf(spaceId: String): SpaceDirs {
    val spaceDir = spaceDirIn(spacesDir, spaceId)
    require(spaceDir.isDirectory) { "no Space with id '$spaceId'; expected a directory at '$spaceDir'" }
    return SpaceDirs(spaceDir)
  }

  private companion object {
    /** An import probe and a spec write - nothing as slow as `SpaceShellEnvironmentCreation`'s pip. */
    val REGISTER_TIMEOUT = 2.minutes
  }
}
