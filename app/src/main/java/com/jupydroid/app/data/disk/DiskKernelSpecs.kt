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

/**
 * A stopped Space's kernelspec catalogue, read off its disk the way its own server would read it -
 * so a spec written into the Space shows in the picker before the Space runs.
 *
 * The directories are [kernelDirsOf]'s. On top of them, the rules are the shipped rootfs's
 * `jupyter_client/kernelspec.py`: `find_kernel_specs` adds the native `python3` when no directory
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
    val found = kernelDirsOf(dirs)
    val specs = found.mapNotNull { (name, dir) -> kernelJsonIn(dir)?.let { kernelSpecOf(name, it) } }
    val native = if (found.none { (name, _) -> name == DEFAULT_KERNEL_ID }) listOf(NATIVE_KERNEL) else emptyList()
    KernelSpecModels(default = DEFAULT_KERNEL_ID, specs = specs + native)
  }

  private companion object {
    /** ipykernel's own spec, which `ensure_native_kernel` lists when no directory holds `python3`. */
    val NATIVE_KERNEL =
      KernelSpec(name = DEFAULT_KERNEL_ID, displayName = DEFAULT_KERNEL_DISPLAY_NAME, language = "python")
  }
}
