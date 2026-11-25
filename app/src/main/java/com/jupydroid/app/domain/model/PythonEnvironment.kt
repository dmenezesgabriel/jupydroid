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
 * @property isMissing the environment was registered and its interpreter has since been deleted: it is
 *   found only through the spec [registeredAs] names, which no picker offers any more.
 */
data class PythonEnvironment(
  val path: String,
  val hasIpykernel: Boolean,
  val registeredAs: String? = null,
  val isMissing: Boolean = false,
) {
  /**
   * The command that adds `ipykernel` with the environment's own pip. Written from `~` because a
   * Space's terminal has the workspace as its HOME (`data/space/SpaceRuntime.kt`), so it pastes there
   * as it reads.
   */
  val ipykernelInstallCommand: String get() = "~/$path/bin/pip install ipykernel"

  /**
   * The command that removes the spec of a [isMissing] environment: the Space's own jupyter_client CLI,
   * which finds the spec on the same search path its server does.
   */
  val kernelRemoveCommand: String get() = "jupyter kernelspec remove $registeredAs"

  /**
   * The kernelspec name a registration gives it: [specPath] lowercased, because
   * `jupyter_client/kernelspec.py` lists a spec under its lowercased directory name, with any
   * character `_kernel_name_pat` (`^[a-z0-9._\-]+$`) folded to `-`, since a directory that
   * pattern refuses is skipped and its kernel lost. `/` is one of those characters, so the path's
   * segments join with `-` - and a project's env keeps its project in the name, so `proj-a/.venv`
   * and `proj-b/.venv` never share one.
   */
  val kernelName: String get() = specPath.lowercase().replace(SPEC_NAME_REFUSED, "-")

  /** `Python (<specPath>)`, as `ipykernel install --display-name` is conventionally used. */
  val kernelDisplayName: String get() = "Python ($specPath)"

  /**
   * The path the spec is named for: [path] minus one leading directory of
   * [ENVIRONMENT_CONTAINER_DIRS] - a container's children are environments, so `envs/stats` is
   * `stats` - while every later segment stays, so `proj-a/envs/stats` and `proj-a/stats` keep
   * distinct names.
   */
  private val specPath: String
    get() = ENVIRONMENT_CONTAINER_DIRS
      .map { "$it/" }
      .firstOrNull(path::startsWith)
      ?.let { path.removePrefix(it) }
      ?: path

  private companion object {
    val SPEC_NAME_REFUSED = Regex("[^a-z0-9._\\-]")
  }
}

/**
 * The directories whose children are Python environments rather than environments themselves -
 * the conventional envs directories in HOME of `globalVirtualEnvronmentLocator.ts`'s
 * `getGlobalVirtualEnvDirs`, both at
 * `microsoft/vscode-python@f4c86e4f5f7c34b99c5333b1aef50e997288f936`. Applied at the workspace root
 * and inside each project directory alike, which is why it lives beside the naming rule that
 * strips one leading match rather than inside the scan that finds them.
 */
internal val ENVIRONMENT_CONTAINER_DIRS = listOf(
  "envs",
  "Envs",
  ".direnv",
  ".venvs",
  ".virtualenvs",
  ".local/share/virtualenvs",
)

/**
 * The environment at [path] among a Space's [environments], once it is shown fit to register - the
 * one set of refusals every `SpaceEnvironments` gives. [kernelHolders] maps each kernel name the
 * Space already has to where it is held; `python3` is always held, by the Space's own kernel.
 *
 * ```
 * registrableEnvironment(listOf(stats), "envs/stats", kernelHolders = emptyMap())   // stats
 * ```
 *
 * @throws IllegalArgumentException when [path] is none of [environments], it is gone, it lacks
 *   ipykernel, or its name is held.
 */
fun registrableEnvironment(
  environments: List<PythonEnvironment>,
  path: String,
  kernelHolders: Map<String, String>,
): PythonEnvironment {
  val environment = requireNotNull(environments.firstOrNull { it.path == path }) {
    "'$path' is no environment of this Space; expected one of ${environments.map { it.path }}"
  }
  require(!environment.isMissing) {
    "'$path' is gone, so no kernel can start in it; expected an environment that is still there"
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
