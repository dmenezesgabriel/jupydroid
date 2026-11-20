package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.PackageReleases
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What every [WheelIndex] must do, whoever implements it. */
abstract class WheelIndexContract {
  /** An index where [packageName] publishes [releases], and no other name publishes anything. */
  protected abstract fun indexServing(packageName: String, releases: PackageReleases): WheelIndex

  private val duckdb =
    PackageReleases(
      latestVersion = "2.0.1",
      wheelNamesByVersion = mapOf("2.0.1" to listOf("duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.whl")),
    )

  @Test
  fun aListedPackageIsItsReleases() = runTest {
    assertEquals(duckdb, indexServing("duckdb", duckdb).releasesOf("duckdb"))
  }

  /** The answer that says nothing, which is what a typo and an unreachable host both get. */
  @Test
  fun aNameTheIndexDoesNotListIsNull() = runTest {
    assertNull(indexServing("duckdb", duckdb).releasesOf("duckdbzz"))
  }

  /**
   * A requirement pip echoes keeps the spelling it was typed with, and PyPI normalizes case and
   * `-`/`_`/`.` runs away (PEP 503), so `Scikit_Learn` and `scikit-learn` are one package.
   */
  @Test
  fun aNameDifferingInCaseOrPunctuationIsTheSamePackage() = runTest {
    val index = indexServing("scikit-learn", duckdb)

    assertEquals(duckdb, index.releasesOf("Scikit_Learn"))
    assertEquals(duckdb, index.releasesOf("SCIKIT.LEARN"))
  }
}
