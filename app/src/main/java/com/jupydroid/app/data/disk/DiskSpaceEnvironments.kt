package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.domain.model.PythonEnvironment
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
 * ```
 */
class DiskSpaceEnvironments(
  private val spacesDir: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpaceEnvironments {
  override suspend fun environments(spaceId: String): List<PythonEnvironment> = withContext(ioDispatcher) {
    val spaceDir = spaceDirIn(spacesDir, spaceId)
    require(spaceDir.isDirectory) { "no Space with id '$spaceId'; expected a directory at '$spaceDir'" }
    val workspace = SpaceDirs(spaceDir).workspace
    ENV_ROOTS.map { File(workspace, it) }
      .flatMap { it.listFiles().orEmpty().asList() }
      .filter { File(it, "pyvenv.cfg").isFile }
      .map { PythonEnvironment(it.relativeTo(workspace).invariantSeparatorsPath, hasIpykernel(it)) }
      .sortedBy { it.path }
  }

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
