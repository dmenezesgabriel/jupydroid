package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelGatewayContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fake is what the UI runs against where no Space can run, so it owes the same contract. */
class FakeKernelGatewayContractTest : KernelGatewayContract() {
  override fun gateway(): KernelGateway = FakeKernelGateway()

  /** No kernel waits on the answer here, so recording it is the whole of what the fake can do. */
  @Test
  fun replyInput_recordsTheAnswerAgainstItsRequest() = runTest {
    val fake = FakeKernelGateway()
    val request = KernelInputRequest(prompt = "Name?", password = false, requestHeader = "{}")

    fake.replyInput(fake.connect(Space(id = "base", name = "Base", kernels = emptyList()), "a.ipynb"), request, "Ada")

    assertEquals(listOf(request to "Ada"), fake.inputReplies)
  }
}
