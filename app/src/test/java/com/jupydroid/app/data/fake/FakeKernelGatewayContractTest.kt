package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelGatewayContract

/** The fake is what the UI runs against where no Space can run, so it owes the same contract. */
class FakeKernelGatewayContractTest : KernelGatewayContract() {
  override fun gateway(): KernelGateway = FakeKernelGateway()
}
