package com.jupydroid.app.domain.port

/**
 * The kind a session was opened as - `SessionContext.IOptions.type`
 * (`packages/apputils/src/sessioncontext.tsx`), declared by whoever opens it and POSTed on
 * `POST api/sessions`.
 *
 * [forPath] is the document rule (`ext === '.ipynb' ? 'notebook' : 'file'`,
 * `packages/docregistry/src/context.ts:86`): what a session gets when its opener does not say
 * otherwise. A console says otherwise - `ConsolePanel` opens its `SessionContext` with
 * `type: 'console'` (`packages/console/src/panel.ts:71`) over a `console-<count>-<uuid>` path
 * the document rule would call a file, and `running:kernel-open-session` reads the server's
 * copy of the field to pick `console:open` over `docmanager:open`
 * (`running-extension/src/kernels.tsx:260-264`).
 */
enum class SessionType {
  NOTEBOOK,
  FILE,
  CONSOLE,
  ;

  /** The literal `IModel.type` carries - lower-case on the wire. */
  val wire get() = name.lowercase()

  companion object {
    fun forPath(path: String): SessionType = if (path.endsWith(".ipynb")) NOTEBOOK else FILE
  }
}

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
