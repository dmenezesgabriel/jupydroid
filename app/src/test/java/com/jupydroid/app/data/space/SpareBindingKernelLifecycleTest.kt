package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.SessionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpareBindingKernelLifecycleTest {
  private val server = FakeKernelLifecycle()
  private val spares = OneSpareKernel(SPARE)
  private val log = RecordingSpaceLifecycleLog()
  private val lifecycle = SpareBindingKernelLifecycle(server, claimSpare = spares::claim, log = log)

  /** A notebook asking for the default kernel binds the spare by id (ADR-0080). */
  @Test
  fun start_bindsTheSpareForARootDocumentAskingForTheDefaultKernel() = runTest {
    val started = lifecycle.start("Cold Path.ipynb")

    assertEquals(listOf<KernelChoice?>(KernelChoice.Running(SPARE.kernelId)), server.startedWith)
    assertEquals(SPARE.kernelId, started.kernelId)
    assertEquals(SPARE.kernelId, log.fieldsOf(KernelLog.SPARE_BOUND)["kernelId"])
  }

  @Test
  fun start_bindsTheSpareForTheSpecItWasStartedFrom() = runTest {
    lifecycle.start("Cold Path.ipynb", KernelChoice.Spec(SPARE.kernelName))

    assertEquals(listOf<KernelChoice?>(KernelChoice.Running(SPARE.kernelId)), server.startedWith)
  }

  @Test
  fun start_startsAnotherSpecByNameAndLeavesTheSpare() = runTest {
    lifecycle.start("Cold Path.ipynb", KernelChoice.Spec("julia"))

    assertEquals(listOf<KernelChoice?>(KernelChoice.Spec("julia")), server.startedWith)
    assertEquals(SPARE, spares.claim { true })
  }

  /**
   * The spare runs at the workspace root, and a bound kernel keeps its own cwd: `create_session`
   * passes over a kernel id it already has (jupyter_server 2.20.0
   * `services/sessions/sessionmanager.py`). A document in a folder would write its files to the root.
   */
  @Test
  fun start_startsADocumentInAFolderByNameAndLeavesTheSpare() = runTest {
    lifecycle.start("reports/Analysis.ipynb")

    assertEquals(listOf<KernelChoice?>(null), server.startedWith)
    assertEquals(SPARE, spares.claim { true })
  }

  @Test
  fun start_joinsARunningKernelItWasAskedForWithoutTouchingTheSpare() = runTest {
    lifecycle.start("Cold Path.ipynb", KernelChoice.Running("kernel-other"))

    assertEquals(listOf<KernelChoice?>(KernelChoice.Running("kernel-other")), server.startedWith)
    assertEquals(SPARE, spares.claim { true })
  }

  /** Only the first session of a server start is faster: there is one spare, not a pool. */
  @Test
  fun start_startsTheSecondSessionByName() = runTest {
    lifecycle.start("Cold Path.ipynb")
    lifecycle.start("Other.ipynb", sessionType = SessionType.NOTEBOOK)

    assertEquals(listOf(KernelChoice.Running(SPARE.kernelId), null), server.startedWith)
  }

  /** Nothing to bind is the normal start, not a failure. */
  @Test
  fun start_startsByNameWhenThereIsNoSpare() = runTest {
    spares.claim { true }

    lifecycle.start("Cold Path.ipynb")

    assertEquals(listOf<KernelChoice?>(null), server.startedWith)
    assertNull(log.fieldsOf(KernelLog.SPARE_BOUND)["kernelId"])
  }

  /**
   * A dead spare is the server's to replace: `create_session` starts the default kernel when the id
   * it was given is not one it has. The log has to say so, or the window reads as a spare's.
   */
  @Test
  fun start_logsAMissWhenTheServerAnsweredWithAnotherKernel() = runTest {
    val missing = SpareBindingKernelLifecycle(SpareLosingServer(), claimSpare = spares::claim, log = log)

    missing.start("Cold Path.ipynb")

    assertEquals(SPARE.kernelId, log.fieldsOf(KernelLog.SPARE_MISSED)["spareKernelId"])
    assertEquals(0, log.countOf(KernelLog.SPARE_BOUND))
  }

  @Test
  fun interrupt_passesThroughToTheServer() = runTest {
    lifecycle.interrupt("kernel-1")

    assertEquals(listOf("kernel-1"), server.interrupted)
  }

  /** `SpaceServer.claimSpareKernel`'s contract: one spare, handed out once, only when accepted. */
  private class OneSpareKernel(private var spare: BareKernel?) {
    fun claim(accepts: (BareKernel) -> Boolean): BareKernel? = spare?.takeIf(accepts)?.also { spare = null }
  }

  /** A server whose spare died: a session asking for it by id gets a fresh default kernel. */
  private class SpareLosingServer(private val server: FakeKernelLifecycle = FakeKernelLifecycle()) :
    KernelLifecycle by server {
    override suspend fun start(documentPath: String, kernel: KernelChoice?, sessionType: SessionType?) =
      server.start(documentPath, null, sessionType)
  }

  private companion object {
    val SPARE = BareKernel(kernelId = "kernel-spare", kernelName = "python3")
  }
}
