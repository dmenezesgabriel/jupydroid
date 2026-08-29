package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalGatewayContract

class FakeTerminalGatewayContractTest : TerminalGatewayContract() {
  override fun gateway(): TerminalGateway = FakeTerminalGateway()
}
