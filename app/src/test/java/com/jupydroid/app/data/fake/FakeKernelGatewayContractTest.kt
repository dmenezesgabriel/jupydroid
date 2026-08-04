package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelGatewayContract

/** The fake is what the UI runs against until Step 10.5, so it owes the same contract. */
class FakeKernelGatewayContractTest : KernelGatewayContract() {
  override fun gateway(): KernelGateway = FakeKernelGateway()
}
