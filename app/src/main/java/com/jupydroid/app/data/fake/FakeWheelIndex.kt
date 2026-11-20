package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.PackageReleases
import com.jupydroid.app.domain.model.normalizedPackageName
import com.jupydroid.app.domain.port.WheelIndex

/**
 * A [WheelIndex] over a fixed map of package name to releases; any other name publishes nothing.
 *
 * `AppContainer`'s default, which lists nothing: a test, and a device that has never asked PyPI,
 * get pip's own text and no note rather than reaching the network.
 *
 * ```
 * FakeWheelIndex(mapOf("duckdb" to duckdbReleases)).releasesOf("DuckDB")   // duckdbReleases
 * ```
 */
class FakeWheelIndex(byName: Map<String, PackageReleases> = emptyMap()) : WheelIndex {
  private val releasesByName = byName.mapKeys { (name, _) -> normalizedPackageName(name) }

  override suspend fun releasesOf(packageName: String): PackageReleases? =
    releasesByName[normalizedPackageName(packageName)]
}
