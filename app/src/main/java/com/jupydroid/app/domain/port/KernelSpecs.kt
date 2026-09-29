package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space

/**
 * `GET api/kernelspecs`' answer - `KernelSpecManager.ISpecs` (`kernelspecs/kernelspec.ts`), as a
 * list: `default` is what a session gets when no kernel is asked for, and [specs] is every
 * kernelspec the server could start, the rows the picker's "Start Kernel" group is built from.
 */
data class KernelSpecModels(val default: String, val specs: List<KernelSpec>) {
  /** The spec named [name], or null - `specs.kernelspecs[name]` in the reference. */
  fun named(name: String): KernelSpec? = specs.firstOrNull { it.name == name }

  /**
   * `Private.getDefaultKernel` (`sessioncontext.tsx:1912-1966`), minus its `autoStartDefault`
   * half - the shipped setting defaults false and this app has no setting of its own, so a
   * preference that resolves to nothing answers **null**, which is what "show the picker" means
   * (`startKernel` returning `true`, `sessioncontext.tsx:740`).
   *
   * ```
   * specs.preferredKernelName(KernelPreference(name = "python3"))      // "python3"
   * specs.preferredKernelName(KernelPreference(name = "xeus-python"))  // null
   * ```
   */
  fun preferredKernelName(preference: KernelPreference): String? {
    preference.name?.let { name -> if (named(name) != null) return name }
    val matching = preference.language?.let { language -> specs.filter { it.language == language } }
      .orEmpty()
    return matching.singleOrNull()?.name
  }
}

/**
 * What a Space can run - the kernelspec catalogue the kernel picker is drawn from.
 *
 * Asked per [Space] because the catalogue is a property of the Space's rootfs, not of the app:
 * `jupyter kernelspec install` into `<space>/usr/share/jupyter/kernels/` adds a row, which is
 * exactly what a per-Space environment is.
 */
interface KernelSpecs {
  /**
   * The Space's kernelspecs - `GET api/kernelspecs` when a server is up, and the Space's own
   * declared list when it is not. **Passive**: answering this must never boot a runtime, the
   * same rule [KernelSessions.running] keeps - a picker opened on a stopped server has an
   * honest answer already.
   */
  suspend fun specs(space: Space): KernelSpecModels
}
