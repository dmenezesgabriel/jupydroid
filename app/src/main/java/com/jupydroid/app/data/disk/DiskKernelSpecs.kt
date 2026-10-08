package com.jupydroid.app.data.disk

import com.jupydroid.app.data.space.SpaceDirs
import com.jupydroid.app.data.space.kernelSpecOf
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_DISPLAY_NAME
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.effectiveDefaultKernel
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * A stopped Space's kernelspec catalogue, read off its disk the way its own server would read it -
 * so a spec written into the Space shows in the picker before the Space runs.
 *
 * The directories are [kernelDirsOf]'s. On top of them, the rules are the shipped rootfs's
 * `jupyter_client/kernelspec.py`: `find_kernel_specs` adds the native `python3` when no directory
 * has it; `get_all_specs` drops a spec that fails to load. One rule is not the server's: a spec whose
 * interpreter is gone ([isInterpreterMissing]) is dropped too, as vscode-jupyter drops it
 * (`vscode-jupyter@f01d80c src/kernels/raw/finder/localKernelSpecFinderBase.node.ts` `loadKernelSpec`)
 * and IntelliJ's interpreter switcher does (`intellij-community@406a27a
 * python/src/com/jetbrains/python/sdk/PySdkRendering.kt` `groupModuleSdksByTypes`), so no default
 * names a kernel that cannot start. The default is the Space's stored one
 * through [effectiveDefaultKernel], the rule the launch also uses for the server's
 * `MultiKernelManager.default_kernel_name`, so the two name the same kernel.
 *
 * Asked per [Space] and found under [spacesDir] by its id, as `DiskSpaceEnvironments` is, so one
 * instance answers the home's Settings for any Space as well as an entered Space's picker.
 *
 * ```
 * DiskKernelSpecs(spacesDir(context), storedDefaultKernelOf = { space -> "stats" }).specs(space)
 * ```
 */
class DiskKernelSpecs(
  private val spacesDir: File,
  /** The default kernel the Space has stored, before [effectiveDefaultKernel] checks it is listed. */
  private val storedDefaultKernelOf: suspend (Space) -> String = { DEFAULT_KERNEL_ID },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KernelSpecs {
  override suspend fun specs(space: Space): KernelSpecModels {
    val stored = storedDefaultKernelOf(space)
    val listed = withContext(ioDispatcher) { listedIn(SpaceDirs(spaceDirIn(spacesDir, space.id))) }
    return KernelSpecModels(default = effectiveDefaultKernel(stored, listed.map { it.name }), specs = listed)
  }

  /** The native `python3` is added when no *kept* spec has the name, so `defaultSpec` always resolves. */
  private fun listedIn(dirs: SpaceDirs): List<KernelSpec> {
    val specs = kernelDirsOf(dirs).mapNotNull { (name, dir) ->
      kernelJsonIn(dir)?.takeUnless { startsNothing(dirs, it) }?.let { kernelSpecOf(name, it) }
    }
    val native = if (specs.none { it.name == DEFAULT_KERNEL_ID }) listOf(NATIVE_KERNEL) else emptyList()
    return specs + native
  }

  private fun startsNothing(dirs: SpaceDirs, spec: JsonObject): Boolean =
    kernelInterpreterOf(spec)?.let { isInterpreterMissing(dirs, it) } == true

  private companion object {
    /** ipykernel's own spec, which `ensure_native_kernel` lists when no directory holds `python3`. */
    val NATIVE_KERNEL =
      KernelSpec(name = DEFAULT_KERNEL_ID, displayName = DEFAULT_KERNEL_DISPLAY_NAME, language = "python")
  }
}
