package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.PythonEnvironment

/**
 * The Python environments a person made inside a Space - what its Settings can offer as kernels.
 *
 * Read off the Space's disk, so it answers a stopped Space and never starts one, as [KernelSpecs]
 * answers a stopped Space's catalogue. Apart from [SpaceRepository], which holds records and reads
 * nothing inside a Space.
 *
 * ```
 * spaceEnvironments.environments("space-1")   // [envs/fast, envs/stats]
 * ```
 */
interface SpaceEnvironments {
  /**
   * Every environment found in the Space [spaceId]'s workspace, ordered by path.
   *
   * @throws IllegalArgumentException when no Space has that id.
   */
  suspend fun environments(spaceId: String): List<PythonEnvironment>
}
