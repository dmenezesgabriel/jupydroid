package com.jupydroid.app.domain.port

/**
 * One row of `GET api/sessions` - `Session.IModel` (`packages/services/src/session/session.ts:381-390`).
 *
 * What the Sessions and Tabs panel draws a kernel session as. The kernel fields are nullable
 * because the model's are: `kernel` may be null when the kernel went but the session record did
 * not, and the panel exists precisely to show that state.
 */
data class KernelSessionModel(
  /** The session's own id - what `DELETE api/sessions/<id>` addresses. */
  val serverSessionId: String,
  /** `kernel.id`, or null when the kernel is already gone. */
  val kernelId: String?,
  /** `kernel.name` - the kernelspec name the row is labelled with. */
  val kernelName: String?,
  /** The document the session runs for, relative to the server's root. */
  val path: String,
  /** `PathExt.basename(path)` as the server recorded it - the row's label. */
  val name: String,
  /** `"notebook"`, `"file"` or `"console"` - the document kind, which picks the row's icon. */
  val type: String,
)
