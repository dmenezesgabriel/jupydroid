package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs

/**
 * The base Space's kernelspec catalogue - `KernelSpec.IManager.specs` reduced to one Space.
 *
 * *Passive*, for the reason [BaseSpaceKernelSessions.running] is: `GET api/kernelspecs` asks a
 * server that is already up, and a stopped one is answered by [whenStopped] instead - the specs on
 * the Space's disk (`data/disk/DiskKernelSpecs.kt`), found the way its server would find them, so
 * the answer is honest rather than a guess, and no picker ever boots a runtime just to draw its rows.
 */
class BaseSpaceKernelSpecs(
  private val sharedSockets: PerRunningServer<KernelSockets>,
  private val whenStopped: KernelSpecs,
) : KernelSpecs {
  override suspend fun specs(space: Space): KernelSpecModels =
    sharedSockets.liveOrNull()?.lifecycle?.kernelSpecs() ?: whenStopped.specs(space)
}
