package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.PackageReleases
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.WheelIndex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The note a cell shows when pip found no distribution and PyPI confirms this Space's platform is
 * why - and the silence for a typo, for a package that installs, and for an index that cannot be
 * asked. The pip text is the tablet's own, escapes and all (measured 2026-11-20).
 */
class PipInstallExplanationTest {
  /** `%pip install muslonly` in a Space, as the app folds it: one stdout stream, pip's red included. */
  private val muslOnlyMiss = listOf(CellOutput.Stream(StreamName.STDOUT, pipMissText("muslonly")))

  /** pip's two ERROR lines and IPython's note, verbatim from the tablet, for any requirement name. */
  private fun pipMissText(requirement: String) =
    "\u001b[31mERROR: Could not find a version that satisfies the requirement $requirement (from versions: none)" +
      "\u001b[0m\u001b[31m\n\u001b[0m\u001b[31mERROR: No matching distribution found for $requirement" +
      "\u001b[0m\u001b[31m\n\u001b[0mNote: you may need to restart the kernel to use updated packages.\n"

  /** A package whose every aarch64 wheel is built for musl - the kernel's glibc cannot run it. */
  private val muslOnlyReleases = PackageReleases(
    latestVersion = "1.0.0",
    wheelNamesByVersion = mapOf(
      "1.0.0" to listOf("muslonly-1.0.0-cp312-cp312-musllinux_1_2_aarch64.whl"),
    ),
  )

  @Test
  fun aConfirmedMissIsANoteNamingThePlatformAndWhatShipsInstead() = runTest {
    val note = PipInstallExplanation(RecordingWheelIndex(mapOf("muslonly" to muslOnlyReleases))).noteFor(muslOnlyMiss)

    val expected = "\nmuslonly ships no manylinux aarch64 wheel for Python 3.12 in any release, and this Space's pip " +
      "installs wheels only, so pip cannot install it here. Latest release 1.0.0 ships musllinux_1_2_aarch64 " +
      "instead, built for musl, which the Space's kernel does not run."
    assertEquals(mapOf("text/plain" to JsonPrimitive(expected)), note?.mimeBundle)
  }

  /** The typo's text is pip's text for the miss, word for word; only PyPI can tell them apart. */
  @Test
  fun aNamePyPiDoesNotHaveIsSilent() = runTest {
    val typo = listOf(CellOutput.Stream(StreamName.STDOUT, pipMissText("muslonlyz")))
    val index = RecordingWheelIndex(mapOf("muslonly" to muslOnlyReleases))

    assertNull(PipInstallExplanation(index).noteFor(typo))
    assertEquals(listOf("muslonlyz"), index.asked)
  }

  @Test
  fun aPackageWithAnInstallableWheelIsSilent() = runTest {
    val pandas = PackageReleases(
      latestVersion = "3.1.1",
      wheelNamesByVersion =
      mapOf("3.1.1" to listOf("pandas-3.1.1-cp312-cp312-manylinux_2_17_aarch64.manylinux2014_aarch64.whl")),
    )
    val text = listOf(CellOutput.Stream(StreamName.STDOUT, pipMissText("pandas")))

    assertNull(PipInstallExplanation(RecordingWheelIndex(mapOf("pandas" to pandas))).noteFor(text))
  }

  /** An unreachable host is null, and null is the answer that says nothing. */
  @Test
  fun anIndexThatCannotBeAskedIsSilent() = runTest {
    assertNull(PipInstallExplanation(RecordingWheelIndex(emptyMap())).noteFor(muslOnlyMiss))
  }

  @Test
  fun twoMissingRequirementsAreTwoParagraphs() = runTest {
    val both = listOf(
      CellOutput.Stream(
        StreamName.STDOUT,
        "ERROR: Could not find a version that satisfies the requirement muslonly (from versions: none)\n" +
          "ERROR: Could not find a version that satisfies the requirement muslsecond (from versions: none)\n",
      ),
    )
    val index = RecordingWheelIndex(
      mapOf("muslonly" to muslOnlyReleases, "muslsecond" to muslOnlyReleases),
    )

    val text = PipInstallExplanation(index).noteFor(both)?.mimeBundle?.get("text/plain")?.toString().orEmpty()

    assertTrue(text, text.contains("muslonly ships no"))
    assertTrue(text, text.contains("muslsecond ships no"))
  }

  /** A cell that printed something else never asks PyPI at all. */
  @Test
  fun aCellThatIsNotPipsMissAsksNothing() = runTest {
    val index = RecordingWheelIndex(mapOf("muslonly" to muslOnlyReleases))

    assertNull(PipInstallExplanation(index).noteFor(listOf(CellOutput.Stream(StreamName.STDOUT, "42\n"))))
    assertEquals(emptyList<String>(), index.asked)
  }

  /** pip prints the requirement as it was typed; the name is what PyPI is asked for. */
  @Test
  fun aPinnedRequirementIsAskedForByItsName() {
    val pinned = listOf(
      CellOutput.Stream(
        StreamName.STDOUT,
        "ERROR: Could not find a version that satisfies the requirement duckdb==2.0.0 (from versions: none)\n",
      ),
    )

    assertEquals(listOf("duckdb"), pipMissingRequirements(pinned))
  }

  /** Versions that exist but do not match a pin is a resolver answer, not a platform miss. */
  @Test
  fun aResolverAnswerWithVersionsIsNotAMiss() {
    val versions = listOf(
      CellOutput.Stream(
        StreamName.STDOUT,
        "ERROR: Could not find a version that satisfies the requirement duckdb==9 (from versions: 2.0.0, 2.0.1)\n",
      ),
    )

    assertEquals(emptyList<String>(), pipMissingRequirements(versions))
  }

  /** Records what was asked of PyPI, so a silent case can prove it never asked. */
  private class RecordingWheelIndex(private val releasesByName: Map<String, PackageReleases>) : WheelIndex {
    val asked = mutableListOf<String>()

    override suspend fun releasesOf(packageName: String): PackageReleases? {
      asked += packageName
      return releasesByName[packageName]
    }
  }
}
