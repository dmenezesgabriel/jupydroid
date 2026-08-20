package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.NotebookExportException
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the export picker is doing, and what it last had to say. Per sitting, like [SearchState]:
 * nbformat has nowhere to record that a notebook was exported.
 */
data class ExportState(
  /** The rows to draw, once a server has been asked - see [offeredExportFormats]. */
  val offered: List<OfferedExportFormat> = emptyList(),
  /** The picker is open and has not been answered yet. Its own field, so a spinner has a state. */
  val loadingFormats: Boolean = false,
  /** The format being converted, or null when nothing is. Names the row so the label can too. */
  val converting: String? = null,
  /** One line for the user, shown once and then dismissed - see [reportSeen]. */
  val report: String? = null,
)

/**
 * `notebook:export-to-format`, and the two things this app does with the result that the reference
 * does not have to.
 *
 * The reference hands the converted bytes to the browser: `window.open(url)` with `download=true`,
 * and where the file goes is the browser's business (`packages/services/src/nbconvert/index.ts`).
 * An Android app has no such place, so the artifact lands **in the workspace, beside the
 * notebook** - where the explorer already lists it and a cell can already `open()` it.
 *
 * Its own collaborator beside [NotebookKernel] and [NotebookCellEdits] for the reason those two
 * are: this shares nothing with them but the state flow. It is not a kernel act - nbconvert reads
 * the *file*, and a notebook with no kernel exports perfectly well.
 *
 * ```
 * viewModel.export.pickerOpened()
 * viewModel.export.exportAs("html")     // -> Analysis.html beside Analysis.ipynb
 * ```
 */
class NotebookExport(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val exporter: NotebookExporter,
  private val workspace: WorkspaceRepository,
  private val scope: CoroutineScope,
  /**
   * Where the notebook is *now*, asked for rather than held: a rename moves the file while this
   * object lives on, and an export of the old path would 404.
   */
  private val notebookId: () -> String,
  /**
   * Flushes the save debounce before a conversion, because nbconvert reads the **file** and the
   * last 500 ms of typing may not be in it yet. The reference does the same thing for the same
   * reason - `if (context.model.dirty) await context.save()`
   * (`packages/notebook-extension/src/index.ts:743-745`).
   */
  private val persist: suspend () -> Unit,
) {
  /**
   * The picker was opened, so ask the Space what it has.
   *
   * **This is the moment that costs**, and it is deliberately not earlier: the Space is started on
   * demand, so asking on every open of the notebook's overflow would boot a runtime to populate a
   * menu nobody asked for. The reference has no such problem - its server is always up and it
   * caches the list on the first notebook (`index.ts:855-890`).
   */
  fun pickerOpened() {
    uiState.update { it.copy(export = it.export.copy(loadingFormats = true, report = null)) }
    scope.launch {
      val offered = runCatching { offeredExportFormats(exporter.formats()) }
      uiState.update {
        it.copy(
          export = it.export.copy(
            loadingFormats = false,
            offered = offered.getOrDefault(emptyList()),
            report = offered.exceptionOrNull()?.let(::readable),
          ),
        )
      }
    }
  }

  /** The picker was dismissed without a pick. What it loaded is kept; re-opening re-asks. */
  fun pickerDismissed() = uiState.update { it.copy(export = it.export.copy(loadingFormats = false)) }

  /**
   * Saves, converts, and writes the result beside the notebook.
   *
   * **An existing file of that name is replaced**, which is `jupyter nbconvert`'s own behaviour on
   * the command line and the only one that does not litter: an export is a derived artifact, and a
   * fourth `Analysis2.html` beside three others answers nobody's question. The report names the
   * file so the replacement is never silent.
   */
  fun exportAs(format: String) {
    uiState.update { it.copy(export = it.export.copy(converting = format, report = null)) }
    scope.launch {
      val written = runCatching {
        persist()
        val exported = exporter.export(notebookId(), format)
        val path = besideNotebook(notebookId(), exported.fileName)
        workspace.writeBytes(path, exported.bytes)
        path
      }
      uiState.update {
        it.copy(
          export = it.export.copy(
            converting = null,
            report = written.fold({ path -> "Exported to $path" }, ::readable),
          ),
        )
      }
    }
  }

  /** The report was shown, so it goes - a snackbar is an event, not a state to sit in. */
  fun reportSeen() = uiState.update { it.copy(export = it.export.copy(report = null)) }

  /**
   * The server's own sentence where there is one, since it is the only thing that says *why* -
   * *"Pandoc wasn't found."* is actionable and "export failed" is not.
   */
  private fun readable(failure: Throwable): String = when (failure) {
    is NotebookExportException -> failure.message.orEmpty()
    else -> "Could not export: ${failure.message ?: failure::class.simpleName}"
  }
}

/**
 * [fileName] in the folder holding [notebookId] - the workspace root when it holds no folder.
 *
 * Beside the notebook rather than at the root, because that is where a user looking for it will
 * be: the explorer opens on the folder the document is in, and `cwd_for_path` gives the kernel the
 * same directory. The name is the **server's**, never derived here - see [ExportedDocument].
 */
internal fun besideNotebook(notebookId: String, fileName: String): String =
  notebookId.substringBeforeLast('/', missingDelimiterValue = "")
    .let { folder -> if (folder.isEmpty()) fileName else "$folder/$fileName" }
