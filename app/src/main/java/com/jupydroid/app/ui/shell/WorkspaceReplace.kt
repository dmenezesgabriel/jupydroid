package com.jupydroid.app.ui.shell

import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.domain.model.editsAgainst
import com.jupydroid.app.domain.model.withEditsApplied
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository

/**
 * How a workspace replace ended.
 *
 * Two arms and not three, because the reference has two: `replaceAll` reports counts on success
 * (`searchView.ts:860-925`) and `_validateBeforePrepare` **throws** on a conflict rather than
 * prompting (`bulkTextEdits.ts:213-216`). An earlier draft of `docs/DECISIONS.md` guessed there was
 * a dialog; reading it said otherwise.
 */
sealed interface ReplaceOutcome {
  /**
   * What was written, counted the way the confirmation counts it.
   *
   * Both numbers, because the reference's own message needs both - *"Replaced {0} occurrences across
   * {1} files"* - and a caller cannot derive one from the other.
   */
  data class Replaced(val occurrences: Int, val fileCount: Int) : ReplaceOutcome

  /**
   * Nothing was written, because [paths] no longer read the way the walk read them.
   *
   * **All or none**: the reference validates every resource before preparing a single task, so one
   * stale file leaves the others exactly as they were. [paths] carries every one that moved on,
   * because the reference's own message names them - *"…because changes were made to {1}"*.
   */
  data class Refused(val paths: List<String>) : ReplaceOutcome
}

/**
 * One resource about to be rewritten: where it lives, which cell of it, and what changes.
 *
 * `ResourceTextEdit` grouped by resource, where a resource is a file **or one cell of a notebook** -
 * `ReplaceService.createEdits` addresses a notebook match through `match.cell?.uri`
 * (`replaceService.ts:206-214`). Built before anything is written, which is what lets the whole
 * operation be refused, and kept as a value so P2x-5d can invert it into one undo entry.
 */
data class ResourceEdits(val path: String, val cell: Int?, val edits: List<TextEdit>)

/**
 * Replace across files, through whatever holds each file's text - **never past an open document**.
 *
 * `IReplaceService` plus the `IBulkEditService` behind it, with the two properties that make the
 * reference's arrangement worth copying:
 *
 * - **The model, not the file.** `BulkTextEdits` resolves a text model for every resource, open or
 *   not (`bulkTextEdits.ts:227`), edits the models and only then saves (`replaceService.ts:136`). A
 *   replace that wrote the file under an open editor would be undone by that editor's next debounced
 *   save, and the reader would watch their replacement disappear.
 * - **Validate everything, then write.** `_validateBeforePrepare` runs over every edit before a
 *   single task is prepared, so a workspace that moved under the walk leaves every file alone rather
 *   than half of them rewritten.
 *
 * **Three routes and the match is what chooses**, with no suffix rule of its own: a document that is
 * open answers for itself; a match naming a **cell** is a notebook and goes through
 * [NotebookRepository], because writing a `.ipynb` as text puts the replacement into the JSON that
 * stores it; anything else is a file and goes through [WorkspaceRepository].
 *
 * Not a port: it implements no mechanism, it composes two of them and the registry - which is what
 * a ViewModel's collaborator is, exactly as `NotebookReplace` is the notebook's.
 *
 * ```
 * WorkspaceReplace(openDocuments, notebooks, workspace).replaceAll(results, "dog", query, false)
 * ```
 */
class WorkspaceReplace(
  private val openDocuments: OpenDocuments,
  private val notebookRepository: NotebookRepository,
  private val workspaceRepository: WorkspaceRepository,
) {
  /**
   * Replaces every match in [files] - **the matches already on screen**, never a query re-run.
   *
   * That distinction is the whole of P2x-1e: a replace that took a query and searched again could
   * rewrite a file against matches nobody saw.
   */
  suspend fun replaceAll(
    files: List<MatchedFile>,
    replaceText: String,
    query: SearchQuery,
    preserveCase: Boolean,
  ): ReplaceOutcome {
    val resources = files.flatMap { it.resources() }
    val planned = resources.map { it to plan(it, replaceText, query, preserveCase) }
    val stale = planned.filter { it.second == null }.map { it.first.path }
    if (stale.isNotEmpty()) return ReplaceOutcome.Refused(stale.distinct())
    planned.forEach { (_, edits) -> write(requireNotNull(edits)) }
    return ReplaceOutcome.Replaced(files.sumOf { it.matches.size }, files.size)
  }

  /**
   * What one resource becomes, or null when it no longer reads the way the walk read it.
   *
   * The read is the routing: an open document is asked, and only a closed one is opened.
   */
  private suspend fun plan(
    resource: Resource,
    replaceText: String,
    query: SearchQuery,
    preserveCase: Boolean,
  ): ResourceEdits? {
    val source = sourceOf(resource) ?: return null
    return resource.matches.editsAgainst(source, replaceText, query, preserveCase)
      ?.let { ResourceEdits(resource.path, resource.cell, it) }
  }

  /**
   * The text a replace is being computed against.
   *
   * **An open document that cannot be read refuses rather than falling through to the file**, which
   * is the point of asking it first: a notebook still loading, or a file whose read failed, holds a
   * model this app cannot see, and writing the file underneath it would be overwritten by its next
   * save. `null` from either side is the conflict `_validateBeforePrepare` refuses.
   */
  private suspend fun sourceOf(resource: Resource): String? {
    val open = openDocuments.at(resource.path)
    // Deliberately not `?:` - an open document answering null must **refuse**, not fall through to
    // the file, which is what `anOpenDocumentThatCannotBeReadRefusesRatherThanFallingBackToTheFile`
    // pins. Reading round it would write under a model this app cannot see.
    return if (open != null) open.sourceOf(resource.cell) else resource.sourceOnDisk()
  }

  /**
   * The text of a resource nothing has open - the two closed routes, and nothing about the open one.
   *
   * A read that fails is null and so a refusal, because the workspace is shared: between the walk
   * and the tap a cell can have removed the file the match was in.
   */
  private suspend fun Resource.sourceOnDisk(): String? = runCatching {
    if (cell == null) {
      workspaceRepository.readText(path)
    } else {
      notebookRepository.readNotebook(path).cells.getOrNull(cell)?.source
    }
  }.getOrNull()

  /** The same three routes, writing rather than reading - see the class docstring. */
  private suspend fun write(edits: ResourceEdits) {
    val open = openDocuments.at(edits.path)
    if (open != null) return open.applyEdits(edits.cell, edits.edits)
    val cell = edits.cell ?: return writeFile(edits)
    val notebook = notebookRepository.readNotebook(edits.path)
    val rewritten = notebook.cells.mapIndexed { at, existing ->
      if (at == cell) existing.copy(source = existing.source.withEditsApplied(edits.edits)) else existing
    }
    // `copy` and never a rebuild: nbformat is an open format, and `Notebook.preserved` and
    // `Cell.preserved` are what carry the fields this app does not model (`NotebookRoundTripTest`).
    notebookRepository.saveNotebook(notebook.copy(cells = rewritten))
  }

  private suspend fun writeFile(edits: ResourceEdits) {
    val text = workspaceRepository.readText(edits.path)
    workspaceRepository.writeText(edits.path, text.withEditsApplied(edits.edits))
  }
}

/**
 * One file's matches split into the resources they are actually in - the file, or its cells.
 *
 * A notebook's matches each name a cell (P2x-4a), and one cell is one resource with one text to
 * validate and rewrite; a plain file's name none, and the file is the single resource. Grouping is
 * what turns `MatchedFile` into what `ResourceTextEdit` addresses.
 */
private data class Resource(val path: String, val cell: Int?, val matches: List<MatchedLine>)

private fun MatchedFile.resources(): List<Resource> =
  matches.groupBy { it.cell }.map { (cell, matches) -> Resource(path, cell, matches) }
