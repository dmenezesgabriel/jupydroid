package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class OfferedExportFormatTest {
  /** The fourteen the tablet's server listed, verbatim. */
  private val whatTheTabletListed = listOf(
    "asciidoc", "custom", "html", "latex", "markdown", "notebook",
    "pdf", "python", "qtpdf", "qtpng", "rst", "script", "slides", "webpdf",
  ).map { ExportFormat(it, "text/plain") }

  @Test
  fun offersFourOfTheFourteenTheDeviceListed() {
    assertEquals(
      listOf("HTML", "Markdown", "Executable Script", "Reveal.js Slides"),
      offeredExportFormats(whatTheTabletListed).map { it.label },
    )
  }

  /** Each of these was measured failing on the device; none may reach a menu. */
  @Test
  fun offersNothingThatCannotConvertOnThisRootfs() {
    val offered = offeredExportFormats(whatTheTabletListed).map { it.id }

    assertEquals(emptyList<String>(), offered.filter { it in CANNOT_CONVERT })
  }

  /** `FORMAT_EXCLUDE` is the reference's own, and this list keeps it. */
  @Test
  fun keepsTheReferencesOwnExclusions() {
    val offered = offeredExportFormats(whatTheTabletListed).map { it.id }

    assertEquals(emptyList<String>(), offered.filter { it in listOf("notebook", "custom") })
  }

  /**
   * The one real departure: the *"Executable Script"* row is served by `python`, because `script`
   * needs `metadata.language_info` and this app writes none. See [OFFERED_EXPORT_FORMATS].
   */
  @Test
  fun servesTheExecutableScriptRowWithPythonRatherThanScript() {
    val offered = offeredExportFormats(whatTheTabletListed)

    assertEquals("python", offered.single { it.label == "Executable Script" }.id)
    assertEquals(emptyList<String>(), offered.map { it.id }.filter { it == "script" })
  }

  /** A Space that lost an exporter loses its row rather than offering a button that 500s. */
  @Test
  fun dropsARowTheServerNoLongerLists() {
    val withoutSlides = whatTheTabletListed.filterNot { it.name == "slides" }

    assertEquals(
      listOf("HTML", "Markdown", "Executable Script"),
      offeredExportFormats(withoutSlides).map { it.label },
    )
  }

  /** A Space that gained pandoc gains nothing here until this list has a label and a measurement. */
  @Test
  fun offersNothingTheAppHasNoLabelFor() {
    val withEverything = whatTheTabletListed + ExportFormat("mermaid", "image/svg+xml")

    assertEquals(4, offeredExportFormats(withEverything).size)
  }

  @Test
  fun offersNothingForAServerThatListsNothing() {
    assertEquals(emptyList<OfferedExportFormat>(), offeredExportFormats(emptyList()))
  }

  private companion object {
    val CANNOT_CONVERT = listOf("asciidoc", "latex", "pdf", "rst", "webpdf", "qtpdf", "qtpng")
  }
}
