package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelPickRequiredException
import com.jupydroid.app.domain.port.KernelPreference
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.SessionType
import com.jupydroid.app.domain.port.StaleKernelSessionException

/**
 * The session a notebook holds: the one it has, and the one it gets when that server is gone.
 *
 * Split out of [NotebookKernel] for the reason that class was split out of `EditorViewModel` -
 * detekt counted the functions and the answer is the object that wants to exist. There is one
 * because the app stops its own server when it is left alone, so "which session is this notebook
 * on" stopped being a field and became a question with an answer that can change underfoot.
 *
 * ```
 * val session = NotebookKernelSession(gateway, BASE_SPACE) { uiState.value.notebookId }
 * session.reopeningIfStale { open -> gateway.execute(open, cell.id, cell.source) }
 * ```
 */
class NotebookKernelSession(
  private val kernelGateway: KernelGateway,
  private val space: Space,
  /**
   * The catalogue a persisted preference is resolved through - `Private.getDefaultKernel`'s
   * `specs` (`sessioncontext.tsx:1927-1964`). A preference that resolves to nothing there is
   * owed to the picker, never POSTed at a server that would only 501 it.
   */
  private val kernelSpecs: KernelSpecs = FakeKernelSpecs(),
  /**
   * Told whenever this notebook gains or loses a kernel, so the bar can say so - see
   * [com.jupydroid.app.ui.editor.kernelStatus].
   *
   * Reported from here rather than by each caller because this is the one object that sees every
   * arrival and every departure: an [open], a [forget], and the drop [reopeningIfStale] makes when
   * a server stopped by the idle backstop refuses a session. A caller-by-caller report would miss that last one,
   * which is precisely the case where the notebook would go on claiming a kernel it has lost.
   *
   * **Declared before [documentPath] despite being the defaulted one**, so the path stays the
   * trailing lambda every call site already writes it as.
   */
  private val onConnectedChanged: (Boolean) -> Unit = {},
  /**
   * `_updateSpec`'s trigger (`notebook/panel.ts:207`): told the session each time a kernel
   * binds - a connect, a [changeKernel], a [rebindIfMatches] from another holder - so the
   * document can write `metadata.kernelspec`. Like [onConnectedChanged] it hangs on [opened]'s
   * setter, because the rebind lands inside this object where no caller could report it.
   */
  private val onBound: (KernelSession) -> Unit = {},
  /**
   * `_pendingKernelName` (`sessioncontext.tsx:590-593`): told the display name of the kernel a
   * connect or `changeKernel({name})` just asked for, so the bar names what is *coming up* for
   * as long as the request is out - `kernelDisplayName` resolves the pending name through the
   * catalogue, not the kernel that is still there. Never fired for a `{id}` attach: the
   * reference sets the pending name only when the model carries one (`:938`).
   */
  private val onPendingKernelName: (String) -> Unit = {},
  /**
   * A session this notebook did not open but already has - a Console sharing the same document's
   * kernel. Given here rather than assigned after construction so [onConnectedChanged]
   * still fires for it: [open]'s "arrived" report lives on [opened]'s setter, and the only way in
   * that reaches the setter is an assignment, which a property initializer is not.
   *
   * `console:inject`'s own contract is the precedent (`console-extension/src/index.ts:1294-1337`):
   * find the session already tracked for a path and run in it, never call `connect` a second time.
   * [open] already returns [opened] without connecting once it is non-null, so handing this in is
   * the whole of what sharing takes - nothing downstream needs to know a session arrived this way.
   */
  attachedTo: KernelSession? = null,
  /**
   * `SessionContext.IOptions.type` (`sessioncontext.tsx`): what the session declares itself on
   * `POST api/sessions` when it *does* connect. Null leaves the document rule - a notebook's
   * path answers `notebook` for it - while a console declares [SessionType.CONSOLE] over a
   * `console-<count>-<uuid>` path that would derive `file`, the same write `ConsolePanel`'s own
   * `type: 'console'` makes (`packages/console/src/panel.ts:71`).
   */
  private val sessionType: SessionType? = null,
  /**
   * `kernelPreference` (`SessionContext.IOptions`, `sessioncontext.tsx`): the kernelspec the
   * notebook was saved with, resolved through [kernelSpecs] on the *first* connect - a null asks
   * for the server's default. Kept in step with the kernel the session is actually bound to, so
   * a stale reconnect re-asks for what the notebook is running now, not what it once ran.
   */
  kernelPreference: KernelPreference? = null,
  /**
   * Read when a session is opened rather than captured, because the notebook's id *is* its path
   * and a rename moves it.
   */
  private val documentPath: () -> String,
) {
  /** @see kernelPreference - mutable because a kernel *change* is what updates it. */
  private var preferredKernel: KernelPreference? = kernelPreference

  /** The preference as it stands - bound kernel once one binds, seeded one before that. */
  var kernelPreference: KernelPreference?
    get() = preferredKernel
    set(value) {
      preferredKernel = value
    }

  /**
   * The open session, or null when this notebook has never asked for a kernel.
   *
   * The setter is what reports it, so no path through this class can change the answer without
   * saying so.
   */
  var opened: KernelSession? = null
    private set(value) {
      field = value
      onConnectedChanged(value != null)
      if (value != null) onBound(value)
    }

  init {
    attachedTo?.let { opened = it }
  }

  /**
   * The notebook's own path goes to the kernel, because that is what gives the kernel a working
   * directory - so a cell writing `data.csv` writes it beside the notebook, which is the whole
   * point of the workspace being the kernel's `HOME`. The kernel keeps the directory it
   * started in if the file is later renamed - a process's cwd is fixed once it is running - which
   * is what happens in the reference too, where a rename retitles the session and leaves the
   * running kernel where it was.
   */
  suspend fun open(): KernelSession {
    opened?.let { return it }
    val catalogue = kernelSpecs.specs(space)
    val choice = resolve(preferredKernel, catalogue)
    reportPending((choice as? KernelChoice.Spec)?.name ?: catalogue.default, catalogue)
    return kernelGateway.connect(space, documentPath(), choice, sessionType).also { opened = it }
  }

  /**
   * `Private.getDefaultKernel` applied to the preference (`sessioncontext.tsx:1927-1964`): the
   * kernelspec name the catalogue resolves it to, as a [KernelChoice.Spec]. **The preference is
   * never sent raw** - a kernelspec the Space no longer has would be a 501
   * (`start_kernel_for_session`, `services/sessions/handlers.py`), so it answers
   * [KernelPickRequiredException] instead, which is `startKernel` returning needsSelection: the
   * picker's cue.
   */
  private fun resolve(preference: KernelPreference?, catalogue: KernelSpecModels): KernelChoice? {
    preference ?: return null
    return catalogue.preferredKernelName(preference)
      ?.let { KernelChoice.Spec(it) }
      ?: throw KernelPickRequiredException(preference.name.orEmpty())
  }

  /** `specs[name]?.display_name ?? name` on the name about to be asked for - see [onPendingKernelName]. */
  private fun reportPending(name: String, catalogue: KernelSpecModels) {
    if (name.isEmpty()) return
    onPendingKernelName(catalogue.named(name)?.displayName ?: name)
  }

  /**
   * `SessionContext.changeKernel` (`sessioncontext.tsx:956-960`): the session is patched when one
   * exists - its id survives, the kernel behind it is swapped - and the choice becomes a connect
   * when none does. The bound kernel's *name* is then the preference, the same write the
   * reference's `_updateSpec` makes to `metadata.kernelspec` (`notebook/panel.ts`).
   */
  suspend fun changeKernel(choice: KernelChoice): KernelSession {
    (choice as? KernelChoice.Spec)?.let { spec ->
      reportPending(spec.name, kernelSpecs.specs(space))
    }
    // A PATCH cannot reach a session whose server is gone, so a stale refusal lands as the
    // *start* the no-session half already is - the chosen kernel connected fresh.
    val bound = try {
      opened?.let { kernelGateway.changeKernel(it, choice) }
    } catch (_: StaleKernelSessionException) {
      opened = null
      null
    } ?: kernelGateway.connect(space, documentPath(), choice, sessionType)
    preferredKernel = KernelPreference(name = bound.kernelName)
    opened = bound
    return bound
  }

  /**
   * `kernelChanged` to [endIfMatches]' `disposed`: the kernel under *this* session was swapped by
   * another holder (a notebook whose kernel a console shares, the picker's `PATCH` issued
   * elsewhere). The session id is unchanged - `opened` takes the rebound session and the
   * preference follows it, or the next use would address a kernel the server already shut down.
   */
  fun rebindIfMatches(rebound: KernelSession) {
    if (opened?.serverSessionId != rebound.serverSessionId) return
    preferredKernel = KernelPreference(name = rebound.kernelName)
    opened = rebound
  }

  /**
   * Runs [use] against the open session, opening a fresh one and trying once more if the session
   * belonged to a server that has since stopped.
   *
   * **The app stopped that server on its own policy, so it brings it back rather than reporting
   * it.** The reference has no equivalent: nothing there stops a server behind the
   * client, so a dead kernel is always news and `SessionContext` displays its error
   * (`packages/apputils/src/sessioncontext.tsx:1167`). Exactly one retry - a second refusal is a
   * real failure and belongs to the caller.
   */
  @Suppress("SwallowedException") // the refusal is the instruction to reconnect, not an error to report
  suspend fun <T> reopeningIfStale(use: suspend (KernelSession) -> T): T =
    try {
      use(open())
    } catch (stale: StaleKernelSessionException) {
      opened = null
      use(open())
    }

  /** Hands back the open session and forgets it, for a caller that is closing the socket. */
  fun forget(): KernelSession? = opened.also { opened = null }

  /**
   * `session.disposed`'s landing - the session ended *elsewhere* (the Running panel's shutdown,
   * the server forgetting it, the server being replaced), so this notebook's claim on it goes
   * and the kernel indicator lands on "No Kernel" the way the reference's does
   * (`sessioncontext.tsx`'s `noKernelName`). A session it is not about is left alone - the
   * emission names one serverSessionId and only a match ends here.
   */
  fun endIfMatches(serverSessionId: String) {
    if (opened?.serverSessionId == serverSessionId) forget()
  }
}
