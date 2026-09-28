package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.flow.Flow

/**
 * What a kernel offers for the code under the cursor.
 *
 * [replacedStart] and [replacedEnd] are the half-open range of the code that a chosen match
 * replaces. Carrying them is not detail: for `np.ar` a kernel answers with whole names, and
 * without the range a caller has to guess how much of the line to drop - which is how a
 * completion turns into `np.np.arange`.
 */
data class CompletionResult(val matches: List<CompletionMatch>, val replacedStart: Int, val replacedEnd: Int) {
  companion object {
    val NONE = CompletionResult(emptyList(), 0, 0)
  }
}

/**
 * One row of a [CompletionResult] - what the user reads versus what accepting it writes, which
 * differ exactly when a language server signature-resolves its labels: jedi answers `join(path,
 * paths)` as the `label` while `insertText` stays `join` (the P5e-0 probe's measured reply).
 * Splicing the label would type the parameter list, so the two are carried separately.
 *
 * [kind] is the LSP `CompletionItemKind` number (Method=2, Function=3, Variable=6, Class=7…),
 * carried raw the way [LanguageDiagnostic.severity] carries its protocol number. It is null on
 * the kernel path, where `matches` are bare strings with nothing to distinguish them by.
 */
data class CompletionMatch(
  /** What accepting the row writes - `insertText`, or `textEdit.newText` when carried. */
  val insert: String,
  /** What the row shows - the server's `label`, which for a kernel match is the string itself. */
  val label: String,
  /** The LSP `CompletionItemKind`, or null when the answer came from the kernel. */
  val kind: Int? = null,
)

/**
 * A live kernel that one notebook (or the Console REPL) is executing against.
 *
 * **Three ids, and they are not interchangeable.** [id] is ours: it identifies this client on the
 * wire and is what every message is signed with. [kernelId] is the server's handle on the process,
 * which `interrupt` and `restart` address. [serverSessionId] is the server's handle on the
 * *session* that owns that process, which is what `shutdown` addresses - `DELETE api/sessions/<id>`
 * takes the kernel with it, and no other id will do.
 */
data class KernelSession(
  val id: String,
  val spaceId: String,
  val kernelId: String,
  /** `kernel.name` in the session model - the kernelspec the kernel answers to. */
  val kernelName: String,
  val serverSessionId: String,
)

/** Events streamed from [KernelGateway.execute]; the UI drives Running/Idle and outputs from these. */
sealed interface KernelEvent {
  data class ExecutionCount(val cellId: String, val count: Int) : KernelEvent

  data class Output(val cellId: String, val output: CellOutput) : KernelEvent

  data object Idle : KernelEvent
}

/** Talks to a real jupyter_server kernel; swapped behind the port in Step 10. */
interface KernelGateway {
  /**
   * Starts a kernel for the document at [documentPath] and binds a session to it.
   *
   * The path is what gives the kernel its **working directory**, so a cell's
   * `open('data.csv', 'w')` lands beside the notebook rather than wherever the server happened to
   * be started - which on the device is a read-only filesystem. JupyterLab reaches the same thing
   * by giving its session the document's path (`SessionContext({path, type: 'notebook'})`).
   */
  suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice? = null): KernelSession

  /**
   * Rebinds [session] to another kernel - `SessionContext.changeKernel`
   * (`packages/apputils/src/sessioncontext.tsx:956-960` over `PATCH api/sessions/<id>`).
   *
   * The returned session is the same *session* - `id` and `serverSessionId` are untouched - with
   * a different kernel behind it: [KernelChoice.Spec] starts a fresh kernel from that spec (the
   * server shuts the old one down), [KernelChoice.Running] joins one already running. The socket
   * bound to the old kernel id goes with it; the next use opens one for the new kernel.
   */
  suspend fun changeKernel(session: KernelSession, kernel: KernelChoice): KernelSession

  suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent>

  suspend fun interrupt(session: KernelSession)

  /**
   * Restarts the kernel behind this session: a fresh interpreter, and **every variable gone**.
   *
   * Distinct from [interrupt], which stops the work and keeps the state. The socket survives, so
   * the session stays valid - it is the process on the other end that is replaced.
   *
   * Outputs and execution counts are deliberately *not* the gateway's business to clear.
   * JupyterLab keeps them on a plain restart and offers "restart and clear" as a separate
   * command (`kernelmenu:restart-and-clear`), so a caller that wants them gone has to say so.
   */
  suspend fun restart(session: KernelSession)

  /**
   * [cursorOffset] is an absolute offset in characters, because that is what both ends of this
   * already speak: a text field's selection and the protocol's own `cursor_pos`. A line and
   * column in between would be two conversions that can only lose.
   */
  suspend fun complete(session: KernelSession, code: String, cursorOffset: Int): CompletionResult

  /**
   * The last [entries] inputs this kernel has run, oldest first.
   *
   * What `notebook:access-previous-history-entry` and `notebook:access-next-history-entry` step
   * through. It is the **kernel's** history and not the notebook's: IPython records every
   * `execute_request` sent with `store_history`, so it spans cells that have since been deleted
   * and sittings this notebook was never open for - which is the whole of what the feature buys
   * over scrolling for the cell that ran something.
   *
   * [entries] is a tail length rather than a page: the reference asks for ten and asks again for
   * twenty when ten were not enough, rather than paging from a cursor
   * (`packages/notebook/src/history.ts:99`, `:248`). Duplicates and filtering belong to the
   * caller; this is the wire's answer, unedited.
   *
   * ```
   * gateway.executionHistory(session, entries = 10)   // ["x = 1", "x + 1"]
   * ```
   */
  suspend fun executionHistory(session: KernelSession, entries: Int): List<String>

  suspend fun disconnect(session: KernelSession)

  /**
   * Ends the session: the kernel process is stopped and its namespace is gone for good.
   *
   * **This is `notebook:shutdown-kernel`, and it is deliberately not what closing a document
   * does.** JupyterLab shuts a kernel down on close only when the `kernelShutdown` setting says
   * so, and that setting's default is **false**
   * (`packages/notebook-extension/schema/tracker.json:741-745`;
   * `SessionContext.dispose()` guards on `shutdownOnDispose`, `sessioncontext.tsx:690`). Leaving a
   * notebook therefore calls [disconnect] - the socket closes and the kernel lives on, which is
   * what lets a reopened notebook reattach to it with its variables intact.
   *
   * ```
   * gateway.shutdown(session)   // the ⋮ menu's "Shut Down Kernel"
   * ```
   */
  suspend fun shutdown(session: KernelSession)
}
