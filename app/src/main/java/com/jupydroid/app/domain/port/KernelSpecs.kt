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
   * The spec [default] names - what a new notebook records when no kernel is chosen, as
   * `createNew` falls back to the server's default (`jupyterlab@7f0e690
   * packages/notebook-extension/src/index.ts`). A default the catalogue does not list is refused
   * rather than written: the notebook would claim a kernel nothing can start.
   *
   * ```
   * KernelSpecModels("python3", listOf(python3)).defaultSpec   // python3
   * ```
   */
  val defaultSpec: KernelSpec
    get() = checkNotNull(named(default)) {
      "default kernel '$default' is not among the specs ${specs.map { it.name }}; expected one of them"
    }

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
   * The Space's kernelspecs - `GET api/kernelspecs` when a server is up, and the kernelspecs on
   * the Space's own disk when it is not. **Passive**: answering this must never boot a runtime, the
   * same rule [KernelSessions.running] keeps - a picker opened on a stopped server has an
   * honest answer already.
   */
  suspend fun specs(space: Space): KernelSpecModels
}
