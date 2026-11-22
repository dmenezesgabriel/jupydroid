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
  /** duckdb as PyPI lists it: every aarch64 wheel it ships is a manylinux one, which the kernel runs. */
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

  /** A package that ships only musllinux wheels: the musl interpreter's, not the kernel's. */
  private val muslOnly =
    PackageReleases(
      latestVersion = "1.0.0",
      wheelNamesByVersion = mapOf(
        "0.9.0" to listOf("muslonly-0.9.0-cp312-cp312-musllinux_1_2_aarch64.whl"),
        "1.0.0" to listOf("muslonly-1.0.0-cp312-cp312-musllinux_1_2_aarch64.whl"),
      ),
    )

  /** duckdb's manylinux wheels fit the kernel, so pip installs it and there is nothing to say. */
  @Test
  fun aManylinuxOnlyPackageIsNotRefused() {
    assertNull(duckdb.platformRefusal("duckdb", WheelTarget.KERNEL))
  }

  /** The wording is the behaviour here, so the sentence is pinned whole rather than in pieces. */
  @Test
  fun aMusllinuxOnlyPackageSaysSoAndNamesWhatItShipsInstead() {
    val expected = "muslonly ships no manylinux aarch64 wheel for Python 3.12 in any release, and this Space's pip " +
      "installs wheels only, so pip cannot install it here. Latest release 1.0.0 ships musllinux_1_2_aarch64 " +
      "instead, built for musl, which the Space's kernel does not run."

    assertEquals(expected, muslOnly.platformRefusal("muslonly", WheelTarget.KERNEL))
  }

  /** pandas publishes a manylinux aarch64 wheel, so pip installs it and there is nothing to say. */
  @Test
  fun aPackageWithAnInstallableWheelIsNotRefused() {
    val pandas = PackageReleases(
      latestVersion = "3.1.1",
      wheelNamesByVersion =
      mapOf("3.1.1" to listOf("pandas-3.1.1-cp312-cp312-manylinux_2_17_aarch64.manylinux2014_aarch64.whl")),
    )

    assertNull(pandas.platformRefusal("pandas", WheelTarget.KERNEL))
  }

  /** An older release that fits is one pip would have installed, so the claim is never made about it. */
  @Test
  fun anOlderReleaseThatFitsSilencesTheClaim() {
    val mixed = PackageReleases(
      latestVersion = "3.0.0",
      wheelNamesByVersion = mapOf(
        "2.0.0" to listOf("thing-2.0.0-cp312-cp312-manylinux_2_17_aarch64.whl"),
        "3.0.0" to listOf("thing-3.0.0-cp312-cp312-musllinux_1_2_aarch64.whl"),
      ),
    )

    assertNull(mixed.platformRefusal("thing", WheelTarget.KERNEL))
  }

  /** psycopg2 is the other case in the same list: it publishes no wheel for anyone. */
  @Test
  fun aPackageThatPublishesSourceOnlySaysSo() {
    val psycopg2 = PackageReleases(latestVersion = "2.9.11", wheelNamesByVersion = mapOf("2.9.11" to emptyList()))

    val refusal = psycopg2.platformRefusal("psycopg2", WheelTarget.KERNEL).orEmpty()

    assertTrue(refusal, refusal.startsWith("psycopg2 publishes source only"))
    assertTrue(refusal, refusal.contains("installs wheels only"))
  }

  /** A release with macOS wheels alone is not a musl problem, and the sentence must not pretend it is. */
  @Test
  fun aLatestReleaseWithNoWheelForTheArchitectureSaysThatInstead() {
    val macOnly = PackageReleases(
      latestVersion = "1.0.0",
      wheelNamesByVersion = mapOf("1.0.0" to listOf("thing-1.0.0-cp312-cp312-macosx_11_0_arm64.whl")),
    )

    val refusal = macOnly.platformRefusal("thing", WheelTarget.KERNEL).orEmpty()

    assertTrue(refusal, refusal.contains("ships no wheel for aarch64 at all"))
    assertFalse(refusal, refusal.contains("built for musl"))
  }

  /** A wheel for another interpreter is named, and no musl clause rides on it. */
  @Test
  fun aWheelForAnotherInterpreterIsNamedWithoutAReason() {
    val otherPython = PackageReleases(
      latestVersion = "1.0.0",
      wheelNamesByVersion = mapOf("1.0.0" to listOf("thing-1.0.0-cp311-cp311-manylinux_2_17_aarch64.whl")),
    )

    val refusal = otherPython.platformRefusal("thing", WheelTarget.KERNEL).orEmpty()

    assertTrue(refusal, refusal.contains("ships manylinux_2_17_aarch64 instead"))
    assertFalse(refusal, refusal.contains("built for musl"))
  }

  /** PEP 503's rule, which is what makes a requirement's own spelling find its package. */
  @Test
  fun aPackageNameIsComparedTheWayPyPiComparesIt() {
    listOf("Scikit_Learn", "scikit-learn", "SCIKIT.LEARN", "scikit--learn").forEach { spelling ->
      assertEquals(spelling, "scikit-learn", normalizedPackageName(spelling))
    }
  }
}
