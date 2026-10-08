package com.jupydroid.app.domain.model

/**
 * A Python environment found in a Space's workspace: a directory holding `pyvenv.cfg`, which both
 * `python3 -m venv` and `uv venv` write.
 *
 * ```
 * PythonEnvironment(path = "envs/stats", hasIpykernel = false).ipykernelInstallCommand
 * ```
 *
 * @property path the environment's root, relative to the Space's workspace - `envs/stats`.
 * @property hasIpykernel whether `ipykernel` is in its own site-packages, without which no kernel can
 *   start in it.
 */
data class PythonEnvironment(val path: String, val hasIpykernel: Boolean) {
  /**
   * The command that adds `ipykernel` with the environment's own pip. Written from `~` because a
   * Space's terminal has the workspace as its HOME (`data/space/SpaceRuntime.kt`), so it pastes there
   * as it reads.
   */
  val ipykernelInstallCommand: String get() = "~/$path/bin/pip install ipykernel"
}
