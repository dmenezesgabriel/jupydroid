package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.domain.model.isKernelName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Every kernelspec directory on a Space's disk, by name, as its server would find them - the rules of
 * the shipped rootfs's `jupyter_client/kernelspec.py`: `_list_kernels_in` keeps a directory holding
 * `kernel.json` whose name matches `_kernel_name_pat`, keyed by its lowercased name, and
 * `find_kernel_specs` keeps a name's first directory. Shared by the stopped picker ([DiskKernelSpecs])
 * and registration ([DiskSpaceEnvironments]), so a name the picker shows is a name registration
 * sees as taken.
 *
 * ```
 * kernelDirsOf(SpaceDirs(spaceDir))   // [("python3", <space>/usr/share/jupyter/kernels/python3)]
 * ```
 */
internal fun kernelDirsOf(dirs: SpaceDirs): List<Pair<String, File>> =
  kernelSearchPath(dirs).flatMap(::kernelDirsIn).distinctBy { (name, _) -> name }

/**
 * Every spec's `argv[0]` to the name its directory holds - the answer both environment
 * registration (`DiskSpaceEnvironments`) and interpreter registration
 * (`SpaceShellKernelRegistration`) read off the same catalogue, so a stopped Space answers
 * whose spec runs which interpreter.
 */
internal fun kernelNameByInterpreter(dirs: SpaceDirs): Map<String, String> =
  kernelDirsOf(dirs).mapNotNull { (name, dir) ->
    kernelJsonIn(dir)?.let(::kernelInterpreterOf)?.let { it to name }
  }.toMap()

/** The spec in [dir]'s `kernel.json`, or null when it does not parse as an object. */
internal fun kernelJsonIn(dir: File): JsonObject? =
  runCatching { Json.parseToJsonElement(File(dir, "kernel.json").readText()).jsonObject }.getOrNull()

/** The program a spec starts, `argv[0]`, or null when its argv names none. */
internal fun kernelInterpreterOf(spec: JsonObject): String? =
  runCatching { spec["argv"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content }.getOrNull()

/**
 * Whether [interpreter], a spec's `argv[0]`, is an absolute path with nothing at it in the Space - an
 * environment deleted after it was registered. The link itself is checked, not where it points: a
 * venv's `bin/python` links to the Space's `/usr/bin/python3`, which the host does not resolve. A bare
 * program name is found through PATH when the kernel starts, so it is never called missing.
 *
 * ```
 * isInterpreterMissing(dirs, "/home/jupydroid/envs/stats/bin/python")   // true once envs/stats is deleted
 * ```
 */
internal fun isInterpreterMissing(dirs: SpaceDirs, interpreter: String): Boolean {
  if (!interpreter.startsWith("/")) return false
  return !Files.exists(File(dirs.spaceDir, interpreter.removePrefix("/")).toPath(), LinkOption.NOFOLLOW_LINKS)
}

/**
 * `jupyter_core/paths.py` `jupyter_path("kernels")` under the environment `SpaceRuntime` gives a
 * Space - HOME is the workspace, PYTHONUSERBASE is `pkgs`, `sys.prefix` is `/usr`, no venv and no
 * `JUPYTER_PATH` - in its order: the user data dir, the user base, then the two system paths
 * (`sys.prefix/share/jupyter` is the second of them, so it is not searched twice).
 */
private fun kernelSearchPath(dirs: SpaceDirs): List<File> = listOf(
  File(dirs.workspace, ".local/share/jupyter"),
  File(dirs.pkgs, "share/jupyter"),
  File(dirs.spaceDir, "usr/local/share/jupyter"),
  File(dirs.spaceDir, "usr/share/jupyter"),
).map { File(it, "kernels") }

private fun kernelDirsIn(kernelsDir: File): List<Pair<String, File>> = kernelsDir.listFiles().orEmpty()
  .filter { isKernelName(it.name) && File(it, "kernel.json").isFile }
  .sortedBy { it.name }
  .map { it.name.lowercase() to it }
