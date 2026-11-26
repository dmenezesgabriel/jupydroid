package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.data.space.SpaceKernelSpec
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.domain.model.ENVIRONMENT_CONTAINER_DIRS
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.isProjectFolderName
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
 * workspace, so the two lists are one - applied at the workspace and inside each project folder,
 * the way vscode-python's locator runs per opened folder. Our workspace is one root, so a project
 * is a top-level directory in it.
 *
 * Fixed roots rather than a walk: the cost is a handful of directory listings per folder however
 * large the workspace grows, and nothing below a root is read - a project's own `.venv` is found,
 * but `proj/sub/.venv` is one level too deep, as it is outside vscode-python's opened folder.
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
    environmentsIn(dirs)
  }

  /**
   * Writes the spec with [SpaceKernelSpec.installEnvironment]. The names already held are every
   * directory [kernelDirsOf] finds, so a name the Space's picker lists is refused here.
   */
  override suspend fun register(spaceId: String, path: String): String = withContext(ioDispatcher) {
    val dirs = spaceDirsOf(spaceId)
    val kernelDirs = kernelDirsOf(dirs)
    val holders = kernelDirs.associate { (name, dir) -> name to "'$dir'" }
    val environment = registrableEnvironment(environmentsIn(dirs), path, holders)
    val name = environment.kernelName
    SpaceKernelSpec.installEnvironment(
      dirs.spaceDir,
      name,
      interpreterOf(dirs, path),
      environment.kernelDisplayName,
      isGnu = environment.isGnu,
    )
    name
  }

  private fun spaceDirsOf(spaceId: String): SpaceDirs {
    val spaceDir = spaceDirIn(spacesDir, spaceId)
    require(spaceDir.isDirectory) { "no Space with id '$spaceId'; expected a directory at '$spaceDir'" }
    return SpaceDirs(spaceDir)
  }

  /**
   * Each found environment, registered under the spec whose `argv[0]` is its own interpreter, and each
   * registered one deleted since ([goneIn]).
   */
  private fun environmentsIn(dirs: SpaceDirs): List<PythonEnvironment> {
    val nameByInterpreter = kernelNameByInterpreter(dirs)
    val found = foundPathsIn(dirs).map {
      val envDir = File(dirs.workspace, it)
      PythonEnvironment(
        it,
        hasIpykernel = hasIpykernel(envDir),
        isGnu = isGnuVenv(envDir),
        registeredAs = nameByInterpreter[interpreterOf(dirs, it)],
      )
    }
    return (found + goneIn(dirs, nameByInterpreter, found.map { it.path }.toSet())).sortedBy { it.path }
  }

  private fun foundPathsIn(dirs: SpaceDirs): List<String> {
    val workspace = dirs.workspace
    return scanBasesIn(workspace)
      .flatMap { base -> ENV_ROOTS.map { File(base, it) } }
      .flatMap { it.listFiles().orEmpty().asList() }
      .filter { File(it, "pyvenv.cfg").isFile }
      .map { it.relativeTo(workspace).invariantSeparatorsPath }
  }

  /**
   * The folders the env roots are applied to: the workspace itself and each project folder - a
   * top-level directory that is not hidden and not itself an env container, whose children the
   * workspace scan already covers. No `.ipynb` or `pyproject.toml` is required of a project: the
   * env's own `pyvenv.cfg` is the signal it holds one.
   */
  private fun scanBasesIn(workspace: File): List<File> {
    val projects = workspace.listFiles().orEmpty().asList()
      .filter { it.isDirectory && it.name.isProjectFolderName() }
    return listOf(workspace) + projects
  }

  /**
   * The environments a spec names as `<workspace>/<path>/bin/python` with nothing left there - what
   * `rm -r` leaves after Register. A spec naming a missing program outside the workspace is no
   * environment of the Space's; the catalogue drops it all the same (`DiskKernelSpecs`).
   */
  private fun goneIn(
    dirs: SpaceDirs,
    nameByInterpreter: Map<String, String>,
    found: Set<String>,
  ): List<PythonEnvironment> {
    val prefix = dirs.inRoot(dirs.workspace) + "/"
    return nameByInterpreter
      .filterKeys { it.startsWith(prefix) && it.endsWith(INTERPRETER) && isInterpreterMissing(dirs, it) }
      .map { (interpreter, name) -> interpreter.removePrefix(prefix).removeSuffix(INTERPRETER) to name }
      .filter { (path, _) -> path !in found }
      .map { (path, name) -> PythonEnvironment(path, hasIpykernel = false, registeredAs = name, isMissing = true) }
  }

  /** The env's `bin/python` as a process in the Space names it - both venv and uv write that link. */
  private fun interpreterOf(dirs: SpaceDirs, path: String): String =
    dirs.inRoot(File(dirs.workspace, path)) + INTERPRETER

  /** ipykernel's package in the env's own site-packages, whichever `python3.X` made it. */
  private fun hasIpykernel(envRoot: File): Boolean = File(envRoot, "lib").listFiles().orEmpty()
    .any { File(it, "site-packages/ipykernel/__init__.py").isFile }

  /**
   * Whether the env's interpreter is the gnu payload's: `pyvenv.cfg`'s `home` is the directory
   * of the interpreter that made it (`venv/__init__.py` writes `sys._base_executable`'s), so
   * `/opt/gnu/...` is glibc and `/usr/bin` musl. Anything else - no `home`, an unknown
   * interpreter - reads musl, which is the env every spec got before the split mattered.
   */
  private fun isGnuVenv(envRoot: File): Boolean {
    val cfg = File(envRoot, "pyvenv.cfg")
    val home = cfg.takeIf { it.isFile }?.useLines { lines ->
      lines.firstNotNullOfOrNull { line -> HOME_LINE.matchEntire(line.trim())?.groupValues?.get(1) }
    } ?: return false
    return home.startsWith(GNU_PAYLOAD_ROOT)
  }

  private companion object {
    const val INTERPRETER = "/bin/python"

    /** The workspace itself, then [ENVIRONMENT_CONTAINER_DIRS], in vscode-python's order. */
    val ENV_ROOTS = listOf("") + ENVIRONMENT_CONTAINER_DIRS

    /** `home = <dir>` in `pyvenv.cfg`; the path never holds a space. */
    val HOME_LINE = Regex("""home\s*=\s*(\S+)""")

    /** The gnu payload's root inside a Space's `/` - where `tools/rootfs/gnu/Dockerfile` installs it. */
    const val GNU_PAYLOAD_ROOT = "/opt/gnu/"
  }
}
