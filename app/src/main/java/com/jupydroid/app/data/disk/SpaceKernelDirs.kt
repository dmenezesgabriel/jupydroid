package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.domain.model.isKernelName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

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

/** The spec in [dir]'s `kernel.json`, or null when it does not parse as an object. */
internal fun kernelJsonIn(dir: File): JsonObject? =
  runCatching { Json.parseToJsonElement(File(dir, "kernel.json").readText()).jsonObject }.getOrNull()

/** The program a spec starts, `argv[0]`, or null when its argv names none. */
internal fun kernelInterpreterOf(spec: JsonObject): String? =
  runCatching { spec["argv"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content }.getOrNull()

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
