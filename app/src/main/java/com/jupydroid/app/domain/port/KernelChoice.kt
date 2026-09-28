package com.jupydroid.app.domain.port

/**
 * Which kernel a session should run - the picker's answer, and `Partial<Kernel.IModel>`'s two
 * interesting halves made explicit.
 *
 * The wire carries it as `kernel: {name: …}` or `kernel: {id: …}` on `POST`/`PATCH
 * api/sessions: [Spec] asks for a kernelspec by name (`start_kernel_for_session`,
 * `services/sessions/handlers.py:patch`), [Running] binds the session to a kernel that is
 * already running - the picker's "Connect to Existing" rows, whose `optionForKernel` value is
 * `{"id": kernel.id}` (`sessioncontext.tsx:1701-1718`). The third shape, `{}`, is "the server
 * picks its default" and is simply `null` here.
 */
sealed interface KernelChoice {
  /** A kernelspec name - `{"kernel":{"name":"python3"}}`. The server starts a fresh kernel. */
  data class Spec(val name: String) : KernelChoice

  /** A live kernel's id - `{"kernel":{"id":"<uuid>"}}`. The session joins what is running. */
  data class Running(val kernelId: String) : KernelChoice
}

/**
 * `ISessionContext.IKernelPreference` (`sessioncontext.tsx:1333-1350`), kept to its two read
 * halves: [name] is `metadata.kernelspec.name` and [language] is `metadata.language_info.name` -
 * `Context._ready` seeds `kernelPreference` from the document model's `defaultKernelName` and
 * `defaultKernelLanguage` (`docregistry/src/context.ts:587-594`).
 *
 * A preference is not a [KernelChoice]: it is *resolved through the spec catalogue* before it
 * reaches the wire - `Private.getDefaultKernel` - so a spec the Space does not have is asked of
 * the user rather than POSTed at a server that would only 501 it. `shouldStart`/`canStart`/
 * `autoStartDefault` are left out deliberately: this app's kernel starts lazily on first use and
 * carries no auto-start setting.
 */
data class KernelPreference(val name: String? = null, val language: String? = null)

/**
 * `startKernel`'s "needsSelection" answer (`sessioncontext.tsx:716-742`): the persisted
 * preference names a spec the Space's catalogue does not have and its language matches no single
 * spec either, so the user must pick - the reference opens `selectKernel` at exactly this point.
 */
class KernelPickRequiredException(val kernelName: String) :
  IllegalStateException(
    "kernelspec '$kernelName' is not in the Space's catalogue; the user must pick a kernel",
  )
