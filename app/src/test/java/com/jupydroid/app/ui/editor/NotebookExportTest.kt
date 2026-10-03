package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.port.ExportedDocument
import com.jupydroid.app.domain.port.NotebookExportException
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookExportTest {
  @Test
  fun pickerOpened_offersWhatTheSpaceHasAndThisAppHasALabelFor() = runTest {
    val export = export()

    export.pickerOpened()

    assertEquals(
      listOf("HTML", "Markdown", "Executable Script", "Reveal.js Slides"),
      state.value.export.offered.map { it.label },
    )
    assertFalse("the picker should not still be loading", state.value.export.loadingFormats)
  }

  /** The Space is started on demand, so the picker has a state to show while it comes up. */
  @Test
  fun pickerOpened_saysItIsLoadingBeforeTheSpaceHasAnswered() {
    val export = export(scope = TestScope())

    export.pickerOpened()

    assertTrue("expected the picker to report itself loading", state.value.export.loadingFormats)
  }

  @Test
  fun pickerOpened_reportsAServerThatCouldNotBeAskedRatherThanShowingNoRows() = runTest {
    val export = export(exporter = ExporterThatCannotBeReached())

    export.pickerOpened()

    assertEquals(emptyList<OfferedExportFormat>(), state.value.export.offered)
    val report = state.value.export.report
    assertTrue("expected a refusal, got $report", report is ExportReport.Refused)
    val reason = (report as ExportReport.Refused).reason
    assertTrue("expected the server's own words, got '$reason'", reason.contains("not running"))
  }

  /**
   * nbconvert reads the **file**, and the last 500 ms of typing may not be in it yet - the
   * reference saves first for the same reason (`index.ts:743-745`).
   */
  @Test
  fun exportAs_savesBeforeItConverts() = runTest {
    val export = export()

    export.exportAs("html")

    assertEquals(listOf("persist", "export"), whatHappened)
  }

  /**
   * The reference's `await context.save()` rejects the export command when the save
   * fails (`index.ts:741-745`), because nbconvert would otherwise read the last-good
   * file while the banner says the write never landed - a conversion of content the
   * user just typed, answered with bytes that predate it, is a silent wrong answer.
   */
  @Test
  fun exportAs_refusesToConvertAFileTheSaveNeverWrote() = runTest {
    val export = export(persistLanded = false)

    export.exportAs("html")

    assertEquals("the export must not run on the stale file", listOf("persist"), whatHappened)
    assertEquals(
      ExportReport.Refused("cannot export to 'html': the notebook's latest edits never reached disk"),
      state.value.export.report,
    )
    assertNull(state.value.export.converting)
  }

  @Test
  fun exportAs_writesTheArtifactBesideTheNotebook() = runTest {
    val export = export(notebookId = "reports/Analysis.ipynb")

    export.exportAs("html")

    assertEquals(ExportReport.Written("reports/Analysis.html", "text/html"), state.value.export.report)
    assertTrue("Analysis.html should be beside the notebook", "reports/Analysis.html" in workspace.paths())
  }

  @Test
  fun exportAs_writesToTheRootForANotebookThatIsInNoFolder() = runTest {
    val export = export(notebookId = "Analysis.ipynb")

    export.exportAs("html")

    assertTrue("Analysis.html should be at the root", "Analysis.html" in workspace.paths())
  }

  /**
   * A markdown export of a notebook with an image is a zip, so
   * nothing on this path may go through a `String`.
   */
  @Test
  fun exportAs_writesBytesUnchangedForAnArtifactThatIsNotText() = runTest {
    val zip = ExportedDocument("Analysis.zip", "application/zip", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))
    val export = export(exporter = FakeNotebookExporter(converted = mapOf("markdown" to zip)))

    export.exportAs("markdown")

    assertArrayEquals(zip.bytes, workspace.readBytes("Analysis.zip"))
  }

  /**
   * The media type comes through with the path, because a share needs it and it cannot be
   * re-derived: a markdown export of a notebook with an image is `application/zip`.
   */
  @Test
  fun exportAs_carriesTheServersMediaTypeIntoTheReport() = runTest {
    val zip = ExportedDocument("Analysis.zip", "application/zip", byteArrayOf(0x50, 0x4B))
    val export = export(exporter = FakeNotebookExporter(converted = mapOf("markdown" to zip)))

    export.exportAs("markdown")

    assertEquals(ExportReport.Written("Analysis.zip", "application/zip"), state.value.export.report)
  }

  /** The server's name, never one derived here: `slides` is `<stem>.slides.html`, two dots. */
  @Test
  fun exportAs_writesUnderTheNameTheServerChose() = runTest {
    val export = export()

    export.exportAs("slides")

    assertTrue("expected the server's own name, got ${workspace.paths()}", "Analysis.slides.html" in workspace.paths())
  }

  /**
   * An export is a derived artifact and `jupyter nbconvert` replaces one on the command line. The
   * report names the file, so the replacement is never silent.
   */
  @Test
  fun exportAs_replacesAnEarlierExportRatherThanCountingUpBesideIt() = runTest {
    val export = export()

    export.exportAs("html")
    export.exportAs("html")

    assertEquals(listOf("Analysis.html"), workspace.paths())
    assertEquals(ExportReport.Written("Analysis.html", "text/html"), state.value.export.report)
  }

  @Test
  fun exportAs_reportsTheServersOwnSentenceWhenTheConversionFails() = runTest {
    val pandoc = "nbconvert failed: Pandoc wasn't found."
    val export = export(exporter = FakeNotebookExporter(refusals = mapOf("markdown" to pandoc)))

    export.exportAs("markdown")

    assertEquals(ExportReport.Refused("cannot export to 'markdown': $pandoc"), state.value.export.report)
    assertEquals(emptyList<String>(), workspace.paths())
  }

  @Test
  fun exportAs_namesTheFormatItIsConvertingWhileItIsConverting() {
    val export = export(scope = TestScope())

    export.exportAs("html")

    assertEquals("html", state.value.export.converting)
  }

  @Test
  fun exportAs_isNoLongerConvertingOnceItHasFinished() = runTest {
    val export = export()

    export.exportAs("html")

    assertNull(state.value.export.converting)
  }

  /** A snackbar is an event, not a state to sit in - a rotation must not show it twice. */
  @Test
  fun reportSeen_clearsTheLineOnceItHasBeenShown() = runTest {
    val export = export()
    export.exportAs("html")

    export.reportSeen()

    assertNull(state.value.export.report)
  }

  /** The picker re-asks on the next open, so a dismissal leaves nothing claiming to be loading. */
  @Test
  fun pickerDismissed_stopsClaimingToBeLoading() {
    val export = export(scope = TestScope())
    export.pickerOpened()

    export.pickerDismissed()

    assertFalse(state.value.export.loadingFormats)
  }

  private val state = MutableStateFlow(EditorUiState())
  private val workspace = RecordingWorkspace()
  private val whatHappened = mutableListOf<String>()

  private fun export(
    exporter: NotebookExporter = RecordingExporter(),
    notebookId: String = "Analysis.ipynb",
    scope: TestScope = TestScope(UnconfinedTestDispatcher()),
    persistLanded: Boolean = true,
  ) = NotebookExport(
    uiState = state,
    exporter = exporter,
    workspace = workspace,
    scope = scope,
    notebookId = { notebookId },
    persist = {
      whatHappened += "persist"
      persistLanded
    },
  )

  /** The fake plus a note of when it was asked, so the save-then-convert order is assertable. */
  private inner class RecordingExporter : NotebookExporter {
    private val delegate = FakeNotebookExporter()

    override suspend fun formats() = delegate.formats()

    override suspend fun export(documentPath: String, format: String): ExportedDocument {
      whatHappened += "export"
      return delegate.export(documentPath, format)
    }
  }

  private class ExporterThatCannotBeReached : NotebookExporter {
    override suspend fun formats() = throw NotebookExportException(null, "the Space is not running")

    override suspend fun export(documentPath: String, format: String) =
      throw NotebookExportException(format, "the Space is not running")
  }

  /** The fake with its writes visible, since [FakeWorkspaceRepository] lists a fixed tree. */
  private class RecordingWorkspace : WorkspaceRepository by FakeWorkspaceRepository() {
    private val written = linkedMapOf<String, ByteArray>()

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
      written[path] = bytes
    }

    override suspend fun readBytes(path: String): ByteArray =
      requireNotNull(written[path]) { "nothing written to '$path'" }

    fun paths(): List<String> = written.keys.toList()
  }
}
