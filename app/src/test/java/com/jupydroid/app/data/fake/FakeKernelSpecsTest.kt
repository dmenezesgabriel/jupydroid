package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.KernelSpecsContract

/**
 * [KernelSpecsContract] against the emulator's answer: no server ever runs there, so the Space's
 * own declared list *is* the catalogue - exactly the stopped-server half of the real adapter.
 */
class FakeKernelSpecsTest : KernelSpecsContract() {
  override fun specs(): KernelSpecs = FakeKernelSpecs()
}
