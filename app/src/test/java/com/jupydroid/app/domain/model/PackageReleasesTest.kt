package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence a Space says when PyPI's own release files confirm the platform is the reason - and
 * the silence when they do not.
 */
class PackageReleasesTest {
  /** duckdb as PyPI lists it: every aarch64 wheel it ships is a glibc one (measured 2026-11-20). */
  private val duckdb =
    PackageReleases(
      latestVersion = "2.0.1",
      wheelNamesByVersion = mapOf(
        "2.0.0" to listOf("duckdb-2.0.0-cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl"),
        "2.0.1" to listOf(
          "duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl",
          "duckdb-2.0.1-cp312-abi3-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl",
        ),
      ),
    )

  /** The wording is the behaviour here, so the sentence is pinned whole rather than in pieces. */
  @Test
  fun aGlbicOnlyPackageSaysSoAndNamesWhatItShipsInstead() {
    val expected = "duckdb ships no musllinux aarch64 wheel for Python 3.12 in any release, and this Space's pip " +
      "installs wheels only, so pip cannot install it here. Latest release 2.0.1 ships manylinux_2_26_aarch64, " +
      "manylinux_2_28_aarch64 instead, built for glibc, which Android's seccomp filter refuses to start."

    assertEquals(expected, duckdb.platformRefusal("duckdb", WheelTarget.SPACE))
  }

  /** pandas publishes a musllinux aarch64 wheel, so pip installs it and there is nothing to say. */
  @Test
  fun aPackageWithAnInstallableWheelIsNotRefused() {
    val pandas = PackageReleases(
      latestVersion = "3.1.1",
      wheelNamesByVersion = mapOf("3.1.1" to listOf("pandas-3.1.1-cp312-cp312-musllinux_1_2_aarch64.whl")),
    )

    assertNull(pandas.platformRefusal("pandas", WheelTarget.SPACE))
  }

  /** An older release that fits is one pip would have installed, so the claim is never made about it. */
  @Test
  fun anOlderReleaseThatFitsSilencesTheClaim() {
    val mixed = PackageReleases(
      latestVersion = "3.0.0",
      wheelNamesByVersion = mapOf(
        "2.0.0" to listOf("thing-2.0.0-cp312-cp312-musllinux_1_2_aarch64.whl"),
        "3.0.0" to listOf("thing-3.0.0-cp312-cp312-manylinux_2_27_aarch64.whl"),
      ),
    )

    assertNull(mixed.platformRefusal("thing", WheelTarget.SPACE))
  }

  /** psycopg2 is the other case in the same list: it publishes no wheel for anyone. */
  @Test
  fun aPackageThatPublishesSourceOnlySaysSo() {
    val psycopg2 = PackageReleases(latestVersion = "2.9.11", wheelNamesByVersion = mapOf("2.9.11" to emptyList()))

    val refusal = psycopg2.platformRefusal("psycopg2", WheelTarget.SPACE).orEmpty()

    assertTrue(refusal, refusal.startsWith("psycopg2 publishes source only"))
    assertTrue(refusal, refusal.contains("installs wheels only"))
  }

  /** A release with macOS wheels alone is not a glibc problem, and the sentence must not pretend it is. */
  @Test
  fun aLatestReleaseWithNoWheelForTheArchitectureSaysThatInstead() {
    val macOnly = PackageReleases(
      latestVersion = "1.0.0",
      wheelNamesByVersion = mapOf("1.0.0" to listOf("thing-1.0.0-cp312-cp312-macosx_11_0_arm64.whl")),
    )

    val refusal = macOnly.platformRefusal("thing", WheelTarget.SPACE).orEmpty()

    assertTrue(refusal, refusal.contains("ships no wheel for aarch64 at all"))
    assertFalse(refusal, refusal.contains("glibc"))
  }

  /** A musllinux wheel for another interpreter is named, and no glibc clause rides on it. */
  @Test
  fun aMusllinuxWheelForAnotherInterpreterIsNamedWithoutAReason() {
    val otherPython = PackageReleases(
      latestVersion = "1.0.0",
      wheelNamesByVersion = mapOf("1.0.0" to listOf("thing-1.0.0-cp311-cp311-musllinux_1_2_aarch64.whl")),
    )

    val refusal = otherPython.platformRefusal("thing", WheelTarget.SPACE).orEmpty()

    assertTrue(refusal, refusal.contains("ships musllinux_1_2_aarch64 instead"))
    assertFalse(refusal, refusal.contains("glibc"))
  }

  /** PEP 503's rule, which is what makes a requirement's own spelling find its package. */
  @Test
  fun aPackageNameIsComparedTheWayPyPiComparesIt() {
    listOf("Scikit_Learn", "scikit-learn", "SCIKIT.LEARN", "scikit--learn").forEach { spelling ->
      assertEquals(spelling, "scikit-learn", normalizedPackageName(spelling))
    }
  }
}
