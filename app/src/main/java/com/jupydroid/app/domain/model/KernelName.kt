package com.jupydroid.app.domain.model

/**
 * Whether a kernelspec can be listed under [name] - the shipped rootfs's
 * `jupyter_client/kernelspec.py` `_kernel_name_pat`, `re.compile(r"^[a-z0-9._\-]+$", re.IGNORECASE)`,
 * which `_list_kernels_in` uses to skip any other directory, so no kernel can have another name.
 *
 * ```
 * isKernelName("stats")      // true
 * isKernelName("my env")     // false
 * ```
 */
fun isKernelName(name: String): Boolean = KERNEL_NAME_PATTERN.matches(name)

/**
 * The kernel a Space's server starts by default: [stored] when [listed] holds it, else `python3`.
 *
 * One rule for the stopped catalogue and the launch, so both name the same kernel. A stored default
 * whose spec was deleted after Save would otherwise reach the server as `default_kernel_name`, and a
 * new notebook would record a kernel nothing can start.
 *
 * ```
 * effectiveDefaultKernel("stats", listOf("python3", "stats"))   // "stats"
 * effectiveDefaultKernel("stats", listOf("python3"))            // "python3"
 * ```
 */
fun effectiveDefaultKernel(stored: String, listed: Collection<String>): String =
  if (stored in listed) stored else DEFAULT_KERNEL_ID

/**
 * Why a Space's server starts [effective] rather than its [stored] default, or null when it starts the
 * stored one: the stored kernel's environment, among [environments], is gone, or no kernel of that name
 * is left. Said on the home, where IntelliJ refuses a run with an invalid interpreter with the reason
 * (`intellij-community@406a27a python/src/com/jetbrains/python/run/AbstractPythonRunConfiguration.java`).
 *
 * ```
 * defaultKernelFallbackNote("stats", effective = "python3", environments = emptyList())
 * // "Default kernel is python3, not stats: no kernel named stats is left in this Space"
 * ```
 */
fun defaultKernelFallbackNote(stored: String, effective: String, environments: List<PythonEnvironment>): String? {
  if (stored == effective) return null
  val gone = environments.firstOrNull { it.isMissing && it.registeredAs == stored }
  val reason = gone?.let { "its environment ~/${it.path} is gone" } ?: "no kernel named $stored is left in this Space"
  return "Default kernel is $effective, not $stored: $reason"
}

private val KERNEL_NAME_PATTERN = Regex("^[a-z0-9._\\-]+$", RegexOption.IGNORE_CASE)
