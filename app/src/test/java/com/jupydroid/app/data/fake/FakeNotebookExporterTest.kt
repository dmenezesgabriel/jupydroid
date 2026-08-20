package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.ExportedDocument
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookExporterContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeNotebookExporterTest : NotebookExporterContract() {
  override fun exporter(
    formats: Map<String, String>,
    converted: Map<String, ExportedDocument>,
    refusals: Map<String, String>,
  ): NotebookExporter = FakeNotebookExporter(formats, converted, refusals)

  /**
   * The default matters more than a fake's usually does: it is what `AppContainer` gives every
   * device that cannot run a Space, so the emulator's export menu is drawn from it.
   */
  @Test
  fun offersWhatTheShippedRootfsConvertsWhenNothingIsAskedOfIt() = runTest {
    val offered = FakeNotebookExporter().formats().map { it.name }

    assertEquals(listOf("html", "markdown", "notebook", "python", "script", "slides"), offered.sorted())
  }

  @Test
  fun namesItsStubForTheDocumentAndTheFormatsOwnExtension() = runTest {
    val exported = FakeNotebookExporter().export("reports/Analysis.ipynb", "markdown")

    assertEquals("Analysis.md", exported.fileName)
    assertEquals("text/markdown", exported.mediaType)
  }

  /** `slides` is `<stem>.slides.html` on the wire, which is two dots and not one. */
  @Test
  fun namesSlidesTheWayTheServerDoes() = runTest {
    assertEquals("Analysis.slides.html", FakeNotebookExporter().export("Analysis.ipynb", "slides").fileName)
  }
}
