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

private val KERNEL_NAME_PATTERN = Regex("^[a-z0-9._\\-]+$", RegexOption.IGNORE_CASE)
