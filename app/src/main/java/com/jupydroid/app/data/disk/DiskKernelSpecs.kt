package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.data.space.kernelSpecOf
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_DISPLAY_NAME
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * A stopped Space's kernelspec catalogue, read off its disk the way its own server would read it -
 * so a spec written into the Space shows in the picker before the Space runs.
 *
 * The rules are the shipped rootfs's `jupyter_client/kernelspec.py`: `_list_kernels_in` keeps a
 * directory holding `kernel.json` whose name matches `_kernel_name_pat`, keyed by its lowercased
 * name; `find_kernel_specs` keeps a name's first directory and adds the native `python3` when none
 * has it; `get_all_specs` drops a spec that fails to load. The default is `python3`, the
 * `MultiKernelManager.default_kernel_name` the server starts with.
 *
 * ```
 * DiskKernelSpecs(SpaceDirs(spaceDirIn(spacesDir(context), "space-1"))).specs(space)
 * ```
 */
class DiskKernelSpecs(private val dirs: SpaceDirs, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
  KernelSpecs {
  override suspend fun specs(space: Space): KernelSpecModels = withContext(ioDispatcher) {
    val found = kernelSearchPath().flatMap(::kernelDirsIn).distinctBy { (name, _) -> name }
    val specs = found.mapNotNull { (name, dir) -> readKernelJson(name, dir) }
    val native = if (found.none { (name, _) -> name == DEFAULT_KERNEL_ID }) listOf(NATIVE_KERNEL) else emptyList()
    KernelSpecModels(default = DEFAULT_KERNEL_ID, specs = specs + native)
  }

  /**
   * `jupyter_core/paths.py` `jupyter_path("kernels")` under the environment `SpaceRuntime` gives a
   * Space - HOME is the workspace, PYTHONUSERBASE is `pkgs`, `sys.prefix` is `/usr`, no venv and no
   * `JUPYTER_PATH` - in its order: the user data dir, the user base, then the two system paths
   * (`sys.prefix/share/jupyter` is the second of them, so it is not searched twice).
   */
  private fun kernelSearchPath(): List<File> = listOf(
    File(dirs.workspace, ".local/share/jupyter"),
    File(dirs.pkgs, "share/jupyter"),
    File(dirs.spaceDir, "usr/local/share/jupyter"),
    File(dirs.spaceDir, "usr/share/jupyter"),
  ).map { File(it, "kernels") }

  private fun kernelDirsIn(kernelsDir: File): List<Pair<String, File>> = kernelsDir.listFiles().orEmpty()
    .filter { KERNEL_NAME_PATTERN.matches(it.name) && File(it, "kernel.json").isFile }
    .sortedBy { it.name }
    .map { it.name.lowercase() to it }

  private fun readKernelJson(name: String, dir: File): KernelSpec? =
    runCatching { Json.parseToJsonElement(File(dir, "kernel.json").readText()).jsonObject }
      .getOrNull()
      ?.let { kernelSpecOf(name, it) }

  private companion object {
    /** `_kernel_name_pat`: `re.compile(r"^[a-z0-9._\-]+$", re.IGNORECASE)`. */
    val KERNEL_NAME_PATTERN = Regex("^[a-z0-9._\\-]+$", RegexOption.IGNORE_CASE)

    /** ipykernel's own spec, which `ensure_native_kernel` lists when no directory holds `python3`. */
    val NATIVE_KERNEL =
      KernelSpec(name = DEFAULT_KERNEL_ID, displayName = DEFAULT_KERNEL_DISPLAY_NAME, language = "python")
  }
}
