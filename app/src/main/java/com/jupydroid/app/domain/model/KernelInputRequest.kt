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
) {
  /**
   * The line [value] leaves in the cell once it is sent: the prompt, a space and the answer, or
   * JupyterLab's mask for a password, as a `stdin` stream - the text `Stdin.handleEvent` builds
   * and `onInputRequest` appends (`jupyterlab@7f0e690 packages/outputarea/src/widget.ts`), so a
   * notebook answered here reads the same there.
   *
   * ```
   * KernelInputRequest("Name?", password = false, requestHeader = "{}").echoOf("Ada")   // "Name? Ada\n"
   * ```
   */
  fun echoOf(value: String): CellOutput.Stream =
    CellOutput.Stream(StreamName.STDIN, "$prompt ${if (password) PASSWORD_MASK else value}\n")
}

/** What JupyterLab writes in place of a `getpass()` answer. */
private const val PASSWORD_MASK = "········"
