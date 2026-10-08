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
 * @property registeredAs the name of the kernelspec that runs this environment's interpreter, or null
 *   when none does.
 */
data class PythonEnvironment(val path: String, val hasIpykernel: Boolean, val registeredAs: String? = null) {
  /**
   * The command that adds `ipykernel` with the environment's own pip. Written from `~` because a
   * Space's terminal has the workspace as its HOME (`data/space/SpaceRuntime.kt`), so it pastes there
   * as it reads.
   */
  val ipykernelInstallCommand: String get() = "~/$path/bin/pip install ipykernel"

  /**
   * The kernelspec name a registration gives it: the directory's name, lowercased because
   * `jupyter_client/kernelspec.py` lists a spec under its lowercased directory name, with any
   * character `_kernel_name_pat` (`^[a-z0-9._\-]+$`) refuses folded to `-`, since a directory that
   * pattern refuses is skipped and its kernel lost.
   */
  val kernelName: String get() = directoryName.lowercase().replace(SPEC_NAME_REFUSED, "-")

  /** `Python (<directory>)`, as `ipykernel install --display-name` is conventionally used. */
  val kernelDisplayName: String get() = "Python ($directoryName)"

  private val directoryName: String get() = path.substringAfterLast('/')

  private companion object {
    val SPEC_NAME_REFUSED = Regex("[^a-z0-9._\\-]")
  }
}

/**
 * The environment at [path] among a Space's [environments], once it is shown fit to register - the
 * one set of refusals every `SpaceEnvironments` gives. [kernelHolders] maps each kernel name the
 * Space already has to where it is held; `python3` is always held, by the Space's own kernel.
 *
 * ```
 * registrableEnvironment(listOf(stats), "envs/stats", kernelHolders = emptyMap())   // stats
 * ```
 *
 * @throws IllegalArgumentException when [path] is none of [environments], it lacks ipykernel, or its
 *   name is held.
 */
fun registrableEnvironment(
  environments: List<PythonEnvironment>,
  path: String,
  kernelHolders: Map<String, String>,
): PythonEnvironment {
  val environment = requireNotNull(environments.firstOrNull { it.path == path }) {
    "'$path' is no environment of this Space; expected one of ${environments.map { it.path }}"
  }
  require(environment.hasIpykernel) {
    "'$path' has no ipykernel, so no kernel can start in it; add it with: ${environment.ipykernelInstallCommand}"
  }
  val name = environment.kernelName
  val holder = kernelHolders[name] ?: SPACE_OWN_KERNEL.takeIf { name == DEFAULT_KERNEL_ID }
  require(holder == null) { "a kernel named '$name' is already held by $holder; expected a name no kernel here has" }
  return environment
}

private const val SPACE_OWN_KERNEL = "the Space's own kernel"
