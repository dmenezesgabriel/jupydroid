package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.data.space.SpaceKernelSpec
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.registrableEnvironment
import com.jupydroid.app.domain.port.SpaceEnvironments
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A Space's environments, found where vscode-python looks for them: the children of the workspace
 * (`workspaceVirtualEnvLocator.ts`) and of the conventional envs directories in HOME
 * (`globalVirtualEnvronmentLocator.ts`, `getGlobalVirtualEnvDirs`), both at
 * `microsoft/vscode-python@f4c86e4f5f7c34b99c5333b1aef50e997288f936`. A Space's HOME *is* its
 * workspace, so the two lists are one.
 *
 * Fixed roots rather than a walk: the cost is a handful of directory listings however large the
 * workspace grows, and nothing below a root is read - a project's own `.venv` is one level too deep,
 * as it is for vscode-python outside its opened folder.
 *
 * ```
 * DiskSpaceEnvironments(spacesDir(context)).environments("space-1")
 * DiskSpaceEnvironments(spacesDir(context)).register("space-1", "envs/stats")
 * ```
 */
class DiskSpaceEnvironments(
  private val spacesDir: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpaceEnvironments {
  override suspend fun environments(spaceId: String): List<PythonEnvironment> = withContext(ioDispatcher) {
    val dirs = spaceDirsOf(spaceId)
    environmentsIn(dirs, kernelDirsOf(dirs))
  }

  /**
   * Writes the spec with [SpaceKernelSpec.installEnvironment]. The names already held are every
   * directory [kernelDirsOf] finds, so a name the Space's picker lists is refused here.
   */
  override suspend fun register(spaceId: String, path: String): String = withContext(ioDispatcher) {
    val dirs = spaceDirsOf(spaceId)
    val kernelDirs = kernelDirsOf(dirs)
    val holders = kernelDirs.associate { (name, dir) -> name to "'$dir'" }
    val environment = registrableEnvironment(environmentsIn(dirs, kernelDirs), path, holders)
    val name = environment.kernelName
    SpaceKernelSpec.installEnvironment(dirs.spaceDir, name, interpreterOf(dirs, path), environment.kernelDisplayName)
    name
  }

  private fun spaceDirsOf(spaceId: String): SpaceDirs {
    val spaceDir = spaceDirIn(spacesDir, spaceId)
    require(spaceDir.isDirectory) { "no Space with id '$spaceId'; expected a directory at '$spaceDir'" }
    return SpaceDirs(spaceDir)
  }

  /** Each found environment, registered under the spec whose `argv[0]` is its own interpreter. */
  private fun environmentsIn(dirs: SpaceDirs, kernelDirs: List<Pair<String, File>>): List<PythonEnvironment> {
    val nameByInterpreter = kernelDirs
      .mapNotNull { (name, dir) -> kernelJsonIn(dir)?.let(::kernelInterpreterOf)?.let { it to name } }
      .toMap()
    val workspace = dirs.workspace
    return ENV_ROOTS.map { File(workspace, it) }
      .flatMap { it.listFiles().orEmpty().asList() }
      .filter { File(it, "pyvenv.cfg").isFile }
      .map { it.relativeTo(workspace).invariantSeparatorsPath }
      .map { PythonEnvironment(it, hasIpykernel(File(workspace, it)), nameByInterpreter[interpreterOf(dirs, it)]) }
      .sortedBy { it.path }
  }

  /** The env's `bin/python` as a process in the Space names it - both venv and uv write that link. */
  private fun interpreterOf(dirs: SpaceDirs, path: String): String =
    dirs.inRoot(File(dirs.workspace, path)) + "/bin/python"

  /** ipykernel's package in the env's own site-packages, whichever `python3.X` made it. */
  private fun hasIpykernel(envRoot: File): Boolean = File(envRoot, "lib").listFiles().orEmpty()
    .any { File(it, "site-packages/ipykernel/__init__.py").isFile }

  private companion object {
    /** The workspace itself, then `getGlobalVirtualEnvDirs`' HOME subdirectories, in its order. */
    val ENV_ROOTS = listOf(
      "",
      "envs",
      "Envs",
      ".direnv",
      ".venvs",
      ".virtualenvs",
      ".local/share/virtualenvs",
    )
  }
}
