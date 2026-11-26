package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Creates a Python environment for a project folder inside a Space and registers its kernel -
 * the person's create act, which [SpaceEnvironments.register] deliberately is not: that one
 * writes a spec for an environment already made, this one makes the environment too.
 *
 * The act runs as a script inside the Space's own shell, so it answers a stopped Space and
 * streams what the script prints - a venv's pip is the slow part, and its lines are the progress
 * a caller shows. [SpaceShellEvent.Finished]'s exit status is the script's own verdict.
 *
 * ```
 * spaceEnvironmentCreation.create("base", "proj-a").toList()
 * ```
 */
interface SpaceEnvironmentCreation {
  /**
   * Creates `<project>/.venv` in [spaceId]'s workspace and registers its kernel, streaming the
   * script's output lines then one [SpaceShellEvent.Finished].
   *
   * @param project a top-level folder of the Space's workspace - the folders
   *   `DiskSpaceEnvironments` scans; anything else is the script's refusal, not this port's.
   * @throws IllegalArgumentException collected when no Space has [spaceId].
   */
  fun create(spaceId: String, project: String): Flow<SpaceShellEvent>
}
