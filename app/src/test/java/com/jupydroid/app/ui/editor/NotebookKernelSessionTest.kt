package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelPickRequiredException
import com.jupydroid.app.domain.port.KernelPreference
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which kernel a notebook is on, and what happens when the server behind it stops. */
class NotebookKernelSessionTest {
  private val space = Space(id = "base", name = "Base", kernels = emptyList())

  /** The catalogue `Private.getDefaultKernel` resolves against - python3, plus a second spec. */
  private val catalogued = Space(
    id = "base",
    name = "Base",
    kernels = listOf(
      KernelSpec(name = "python3", displayName = "Python 3", language = "python"),
      KernelSpec(name = "julia", displayName = "Julia", language = "julia"),
    ),
  )

  /** Two specs sharing one language, so no single language match can stand in for a lost name. */
  private val twoPythons = catalogued.copy(
    kernels = catalogued.kernels + KernelSpec(name = "python3-conda", displayName = "Conda", language = "python"),
  )

  @Test
  fun open_connectsOnceAndKeepsTheSameSession() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 0)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }

    assertEquals(session.open(), session.open())
    assertEquals(1, gateway.connectCount)
  }

  @Test
  fun open_returnsThePreAttachedSessionWithoutConnecting() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 0)
    val attached = KernelSession(
      id = "s1",
      spaceId = "base",
      kernelId = "k1",
      kernelName = "python3",
      serverSessionId = "ss1",
    )
    val session = NotebookKernelSession(gateway, space, attachedTo = attached) { "Analysis.ipynb" }

    assertEquals(attached, session.open())
    assertEquals(0, gateway.connectCount)
  }

  @Test
  fun constructingWithAttachedToReportsTheKernelArrived() {
    val reported = mutableListOf<Boolean>()
    val attached = KernelSession(
      id = "s1",
      spaceId = "base",
      kernelId = "k1",
      kernelName = "python3",
      serverSessionId = "ss1",
    )

    NotebookKernelSession(
      StaleSessionKernelGateway(refusals = 0),
      space,
      onConnectedChanged = { reported += it },
      attachedTo = attached,
    ) { "Analysis.ipynb" }

    assertEquals(listOf(true), reported)
  }

  @Test
  fun reopeningIfStale_reconnectsAPreAttachedSessionThatProvesStale() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val attached = KernelSession(
      id = "stale-attached",
      spaceId = "base",
      kernelId = "k0",
      kernelName = "python3",
      serverSessionId = "ss0",
    )
    val session = NotebookKernelSession(gateway, space, attachedTo = attached) { "Analysis.ipynb" }

    val events = session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2") }.toList()

    assertEquals("the pre-attached session was never counted, only its replacement was", 1, gateway.connectCount)
    assertTrue("expected the retry to produce the cell's events", events.isNotEmpty())
  }

  @Test
  fun forget_handsBackThePreAttachedSessionAndLeavesNone() {
    val attached = KernelSession(
      id = "s1",
      spaceId = "base",
      kernelId = "k1",
      kernelName = "python3",
      serverSessionId = "ss1",
    )
    val session = NotebookKernelSession(StaleSessionKernelGateway(refusals = 0), space, attachedTo = attached) {
      "Analysis.ipynb"
    }

    assertEquals(attached, session.forget())
    assertNull(session.opened)
  }

  @Test
  fun open_asksForThePathTheNotebookHasNowRatherThanTheOneItHadThen() = runTest {
    var path = "Draft.ipynb"
    val gateway = RecordingConnectKernelGateway()
    val session = NotebookKernelSession(gateway, space) { path }

    session.open()
    session.forget()
    path = "Renamed.ipynb"
    session.open()

    assertEquals(listOf("Draft.ipynb", "Renamed.ipynb"), gateway.paths)
  }

  @Test
  fun reopeningIfStale_opensAFreshSessionAndTriesAgain() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }

    val events = session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2") }.toList()

    assertEquals(2, gateway.connectCount)
    assertTrue("expected the retry to produce the cell's events", events.isNotEmpty())
  }

  @Test
  fun reopeningIfStale_letsASecondRefusalThrough() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 2)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }

    val failure = runCatching { session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2") } }

    assertTrue(
      "expected the second refusal to reach the caller, got ${failure.exceptionOrNull()}",
      failure.exceptionOrNull() is StaleKernelSessionException,
    )
    assertEquals("and no third attempt", 2, gateway.connectCount)
  }

  /**
   * The kernelspec the notebook was saved with is `kernelPreference`
   * (`sessioncontext.tsx`'s `SessionContext.IOptions`) - what `open` asks the server to start
   * rather than leaving the choice to the server's default. Resolved through the Space's
   * catalogue like `Private.getDefaultKernel`, so only a name the server actually has is sent.
   */
  @Test
  fun open_startsTheKernelTheNotebookPrefers() = runTest {
    val gateway = FakeKernelGateway()
    val session = NotebookKernelSession(gateway, catalogued, kernelPreference = KernelPreference(name = "julia")) {
      "nb.ipynb"
    }

    val open = session.open()

    assertEquals(listOf(KernelChoice.Spec("julia")), gateway.connectsAskedFor)
    assertEquals("julia", open.kernelName)
  }

  /**
   * `getDefaultKernel`'s language fallback (`sessioncontext.tsx:1940-1961`): the persisted name
   * is gone from the catalogue - a kernelspec uninstalled since the notebook was last opened -
   * and exactly one spec still speaks its language, so it stands in rather than asking.
   */
  @Test
  fun open_resolvesAPreferenceWhoseSpecIsGoneThroughItsLanguage() = runTest {
    val gateway = FakeKernelGateway()
    val session = NotebookKernelSession(
      gateway,
      catalogued,
      kernelPreference = KernelPreference(name = "xeus-python", language = "python"),
    ) { "nb.ipynb" }

    val open = session.open()

    assertEquals(listOf(KernelChoice.Spec("python3")), gateway.connectsAskedFor)
    assertEquals("python3", open.kernelName)
  }

  /**
   * The other half of `getDefaultKernel`: a name nothing resolves - no exact match, no single
   * language match - is owed to the *picker*, not the wire (`startKernel` answering
   * needsSelection, `sessioncontext.tsx:746-747`). POSTing it would only 501 against
   * `start_kernel_for_session`.
   */
  @Test
  fun open_owesAPickWhenThePreferenceResolvesToNothing() = runTest {
    val gateway = FakeKernelGateway()
    val session = NotebookKernelSession(
      gateway,
      catalogued,
      kernelPreference = KernelPreference(name = "xeus-python"),
    ) { "nb.ipynb" }

    val failure = runCatching { session.open() }.exceptionOrNull()

    assertTrue("expected KernelPickRequiredException, got $failure", failure is KernelPickRequiredException)
    assertEquals("xeus-python", (failure as KernelPickRequiredException).kernelName)
    assertEquals(
      "a spec the Space lacks must never reach the server",
      emptyList<KernelChoice>(),
      gateway.connectsAskedFor,
    )
  }

  /** Same answer when the language matches *two* specs - no honest way to pick between them. */
  @Test
  fun open_owesAPickWhenItsLanguageMatchesMoreThanOneSpec() = runTest {
    val gateway = FakeKernelGateway()
    val session = NotebookKernelSession(
      gateway,
      twoPythons,
      kernelPreference = KernelPreference(name = "xeus-python", language = "python"),
    ) { "nb.ipynb" }

    val failure = runCatching { session.open() }.exceptionOrNull()

    assertTrue("expected KernelPickRequiredException, got $failure", failure is KernelPickRequiredException)
    assertEquals(emptyList<KernelChoice>(), gateway.connectsAskedFor)
  }

  /**
   * `_updateSpec`'s trigger (`notebook/panel.ts:207`): every bind - the first connect, a
   * `changeKernel`, a rebind announced by another holder - is reported so the document can write
   * `metadata.kernelspec`. The *session* reports rather than each caller, the same reason
   * [NotebookKernelSession.onConnectedChanged] lives on the setter: a rebind arrives inside this
   * object where no caller can see it.
   */
  @Test
  fun everyBoundKernelIsReported() = runTest {
    val bound = mutableListOf<String>()
    val session = NotebookKernelSession(
      FakeKernelGateway(),
      catalogued,
      onBound = { bound += it.kernelName },
    ) { "nb.ipynb" }

    session.open()
    session.changeKernel(KernelChoice.Spec("julia"))
    session.rebindIfMatches(session.opened!!.copy(kernelId = "k9", kernelName = "r9"))

    assertEquals(listOf("python3", "julia", "r9"), bound)
  }

  /**
   * `SessionContext.changeKernel`: the session is patched, not reconnected - its own id is
   * untouched, the kernel behind it is the new one.
   */
  @Test
  fun changeKernel_swapsTheKernelUnderTheSameSession() = runTest {
    val gateway = FakeKernelGateway()
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }
    val open = session.open()

    session.changeKernel(KernelChoice.Spec("julia"))

    assertEquals(listOf(KernelChoice.Spec("julia")), gateway.kernelChanges)
    assertEquals(open.id, session.opened?.id)
    assertEquals(open.serverSessionId, session.opened?.serverSessionId)
    assertEquals("julia", session.opened?.kernelName)
  }

  /** Picking a kernel with none running is a *start*, not a patch - `startNew` in the reference. */
  @Test
  fun changeKernel_connectsWithTheChoiceWhenNoSessionIsOpen() = runTest {
    val gateway = FakeKernelGateway()
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }

    session.changeKernel(KernelChoice.Spec("julia"))

    assertEquals(listOf(KernelChoice.Spec("julia")), gateway.connectsAskedFor)
    assertEquals("julia", session.opened?.kernelName)
  }

  /**
   * A PATCH cannot reach a session whose server is gone, so the pick lands as the *start* the
   * no-session half already is - the chosen kernel connected fresh, not a
   * `StaleKernelSessionException` out of the picker's launch with `kernelChanging` left latched.
   */
  @Test
  fun changeKernel_connectsTheChoiceWhenTheOpenSessionProvesStale() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 0)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }
    session.open()
    gateway.serverWasReplaced()

    session.changeKernel(KernelChoice.Spec("julia"))

    assertEquals(listOf(null, KernelChoice.Spec("julia")), gateway.connectsAskedFor)
    assertEquals("julia", session.opened?.kernelName)
    assertEquals("session-2", session.opened?.id)
  }

  /**
   * The picked spec becomes the preference a reconnect re-asks for: `_updateSpec` writes
   * `kernel.name` to `metadata.kernelspec` in the reference (`notebook/panel.ts`), so what the
   * notebook is bound to *is* what it prefers.
   */
  @Test
  fun aChangedKernelIsWhatAReconnectReopensWith() = runTest {
    val gateway = FakeKernelGateway()
    val session = NotebookKernelSession(gateway, catalogued) { "Analysis.ipynb" }
    session.open()
    session.changeKernel(KernelChoice.Spec("julia"))
    session.forget()

    session.open()

    assertEquals(listOf(null, KernelChoice.Spec("julia")), gateway.connectsAskedFor)
  }

  /**
   * `kernelChanged` beside `session.disposed`: another holder of the same session - the notebook
   * whose kernel this console shares, the picker on a different document - patched it, and the
   * rebound carries the new kernel id, so this copy swaps rather than pointing at a kernel the
   * server already shut down.
   */
  @Test
  fun rebindIfMatches_swapsTheKernelUnderTheOpenSession() = runTest {
    val session = NotebookKernelSession(FakeKernelGateway(), space) { "Analysis.ipynb" }
    val open = session.open()
    val rebound = open.copy(kernelId = "k9", kernelName = "julia")

    session.rebindIfMatches(rebound)

    assertEquals(rebound, session.opened)
  }

  @Test
  fun rebindIfMatches_leavesASessionItIsNotAboutAlone() = runTest {
    val session = NotebookKernelSession(FakeKernelGateway(), space) { "Analysis.ipynb" }
    val open = session.open()

    session.rebindIfMatches(open.copy(serverSessionId = "somebody else's", kernelId = "k9"))

    assertEquals(open, session.opened)
  }

  @Test
  fun forget_handsBackTheOpenSessionAndLeavesNone() = runTest {
    val session = NotebookKernelSession(StaleSessionKernelGateway(refusals = 0), space) { "Analysis.ipynb" }
    val open = session.open()

    assertEquals(open, session.forget())
    assertNull(session.opened)
  }

  /** An ask about the kernel already running - nothing to start one for if none is. */
  @Test
  fun whileOpen_usesTheOpenSessionAndOpensNone() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 0)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }
    val used = mutableListOf<KernelSession>()

    session.whileOpen { used += it }
    val open = session.open()
    session.whileOpen { used += it }

    assertEquals(listOf(open), used)
    assertEquals(1, gateway.connectCount)
  }

  /** A stale session has no kernel left to ask: it is let go, not reopened - and nothing throws. */
  @Test
  fun whileOpen_letsGoOfASessionThatProvesStale() = runTest {
    val session = NotebookKernelSession(StaleSessionKernelGateway(refusals = 0), space) { "Analysis.ipynb" }
    session.open()

    session.whileOpen { throw StaleKernelSessionException(it.id) }

    assertNull(session.opened)
  }

  @Test
  fun forget_hasNothingToHandBackWhenNoKernelWasEverOpened() {
    val session = NotebookKernelSession(StaleSessionKernelGateway(refusals = 0), space) { "Analysis.ipynb" }

    assertNull(session.forget())
  }

  /**
   * **Every arrival and every departure is reported**, which is why the report lives on the setter
   * rather than at each call site - see [NotebookKernelSession.onConnectedChanged]. The sequence a
   * stale reconnect produces is the case a per-caller report would miss: the refusal drops the
   * session inside [NotebookKernelSession.reopeningIfStale], where no caller can see it, and a
   * notebook that went on claiming a kernel there would say *Idle* with no kernel at all.
   */
  @Test
  fun aStaleReconnectReportsTheKernelGoingAndComingBack() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val reported = mutableListOf<Boolean>()
    val session =
      NotebookKernelSession(gateway, space, onConnectedChanged = { reported += it }) { "Analysis.ipynb" }

    session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2").toList() }

    assertEquals(listOf(true, false, true), reported)
  }

  /**
   * `SessionContext`'s answer to `session.disposed`: the session ended *elsewhere* - the Running
   * panel's shutdown, the server forgetting it - and the notebook holding it lands on No Kernel
   * rather than pointing at a session that is not there (`sessioncontext.tsx`'s `noKernelName`).
   */
  @Test
  fun endIfMatches_forgetsTheSessionAndReportsTheKernelGone() = runTest {
    val reported = mutableListOf<Boolean>()
    val session = NotebookKernelSession(
      StaleSessionKernelGateway(refusals = 0),
      space,
      onConnectedChanged = { reported += it },
    ) { "Analysis.ipynb" }
    val open = session.open()

    session.endIfMatches(open.serverSessionId)

    assertNull(session.opened)
    assertEquals(listOf(true, false), reported)
  }

  @Test
  fun endIfMatches_leavesASessionItIsNotAboutAlone() = runTest {
    val reported = mutableListOf<Boolean>()
    val session = NotebookKernelSession(
      StaleSessionKernelGateway(refusals = 0),
      space,
      onConnectedChanged = { reported += it },
    ) { "Analysis.ipynb" }
    session.open()

    session.endIfMatches("somebody else's")

    assertEquals(listOf(true), reported)
  }

  @Test
  fun endIfMatches_onNothingOpenReportsNothing() {
    val reported = mutableListOf<Boolean>()
    val session = NotebookKernelSession(
      StaleSessionKernelGateway(refusals = 0),
      space,
      onConnectedChanged = { reported += it },
    ) { "Analysis.ipynb" }

    session.endIfMatches("fake-session-base")

    assertEquals(emptyList<Boolean>(), reported)
  }

  @Test
  fun forgettingReportsTheKernelGone() = runTest {
    val reported = mutableListOf<Boolean>()
    val session = NotebookKernelSession(
      StaleSessionKernelGateway(refusals = 0),
      space,
      onConnectedChanged = { reported += it },
    ) { "Analysis.ipynb" }

    session.open()
    session.forget()

    assertEquals(listOf(true, false), reported)
  }
}
