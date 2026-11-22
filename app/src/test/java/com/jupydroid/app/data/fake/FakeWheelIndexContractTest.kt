package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.PackageReleases
import com.jupydroid.app.domain.port.WheelIndex
import com.jupydroid.app.domain.port.WheelIndexContract

class FakeWheelIndexContractTest : WheelIndexContract() {
  override fun indexServing(packageName: String, releases: PackageReleases): WheelIndex =
    FakeWheelIndex(mapOf(packageName to releases))
}
