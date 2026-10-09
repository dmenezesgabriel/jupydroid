package com.jupydroid.app.domain.model

/**
 * A kernel blocked inside `input()` or `getpass()`, asking the user for a line.
 *
 * [requestHeader] is the request's own header as the kernel sent it, kept as opaque JSON because
 * the answer has to carry it back verbatim as `parent_header` - which is what JupyterLab's
 * `sendInputReply` sends (`jupyterlab@7f0e690 packages/services/src/kernel/default.ts`). The
 * domain never reads inside it; only the adapter that wrote it does.
 *
 * ```
 * KernelInputRequest(prompt = "Name?", password = false, requestHeader = """{"msg_id":"r1"}""")
 * ```
 */
data class KernelInputRequest(
  val prompt: String,
  /** `getpass()` rather than `input()`: what is typed must not be shown. */
  val password: Boolean,
  val requestHeader: String,
)
