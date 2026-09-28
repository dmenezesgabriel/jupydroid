package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs

/**
 * The [KernelSpecs] answer where no server can run: the Space's own declared [Space.kernels],
 * which is also what the real adapter falls back to while its server is stopped - a picker on a
 * device with no Space still lists an honest "python3".
 */
class FakeKernelSpecs(
  /** A catalogue other than the Space's declared list, for cases about specs the Space lacks. */
  private val declared: KernelSpecModels? = null,
) : KernelSpecs {
  override suspend fun specs(space: Space): KernelSpecModels = declared ?: KernelSpecModels(
    default = space.kernels.firstOrNull()?.name ?: "",
    specs = space.kernels,
  )
}
