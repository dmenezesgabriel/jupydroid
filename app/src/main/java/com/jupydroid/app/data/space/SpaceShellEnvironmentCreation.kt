package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.minutes

/**
 * [SpaceEnvironmentCreation] over [SpaceShell]: the create is the Space's own `jupydroid-env`
 * helper - installed at `/usr/local/bin` by `installOnStartOf`
 * (`AndroidSpaceStartInstall.kt`), so the terminal runs the same bytes by name - asked to make
 * `<project>/.venv` and register its kernel. This adapter owns the command's shape - the helper's
 * name, the create verb, the quoting and the bound; the script owns what making an environment is.
 */
class SpaceShellEnvironmentCreation(private val spaceShell: SpaceShell) : SpaceEnvironmentCreation {
  override fun create(spaceId: String, project: String): Flow<SpaceShellEvent> =
    spaceShell.run(spaceId, "jupydroid-env create ${project.shellQuoted()}", CREATE_TIMEOUT)

  private companion object {
    /** A venv's pip is the slow part; the bound is the harness installs' (`SpaceHarnessesViewModel`). */
    val CREATE_TIMEOUT = 5.minutes
  }
}
