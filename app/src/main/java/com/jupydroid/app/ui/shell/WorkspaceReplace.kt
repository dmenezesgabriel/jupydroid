package com.jupydroid.app.ui.shell

import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.domain.model.editsAgainst
import com.jupydroid.app.domain.model.invertedAgainst
import com.jupydroid.app.domain.model.withEditsApplied
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository

/**
 * How a workspace replace ended.
 *
 * Two arms and not three, because the reference has two: `replaceAll` reports counts on success
 * (`searchView.ts:860-925`) and `_validateBeforePrepare` **throws** on a conflict rather than
 * prompting (`bulkTextEdits.ts:213-216`). An earlier reading of this assumed a dialog; the source
 * says otherwise.
 */
sealed interface ReplaceOutcome {
  /**
   * What was written, counted the way the confirmation counts it.
   *
   * Both numbers, because the reference's own message needs both - *"Replaced {0} occurrences across
   * {1} files"* - and a caller cannot derive one from the other.
   */
  data class Replaced(val occurrences: Int, val fileCount: Int, val undo: ReplaceUndo) : ReplaceOutcome

  /**
   * Nothing was written, because [paths] no longer read the way the walk read them.
   *
   * **All or none**: the reference validates every resource before preparing a single task, so one
   * stale file leaves the others exactly as they were.
   *
   * **Naming every one is a departure, and a small one.** `_validateBeforePrepare` throws on the
   * first mismatch it meets - *"&lt;uri&gt; has changed in the meantime"* (`bulkTextEdits.ts:216`) - so
   * the reference's *replace* names one resource. Its *undo* names them all, *"…because changes were
   * made to {1}"* (`undoRedoService.ts:858-869`), and a reader told which files moved on can act on
   * it where a reader told about the first cannot. [UndoOutcome.Refused] is the same list for the
   * message it is actually quoting.
   */
  data class Refused(val paths: List<String>) : ReplaceOutcome
}

/**
 * One resource about to be rewritten: where it lives, which cell of it, and what changes.
 *
 * `ResourceTextEdit` grouped by resource, where a resource is a file **or one cell of a notebook** -
 * `ReplaceService.createEdits` addresses a notebook match through `match.cell?.uri`
 * (`replaceService.ts:206-214`). Built before anything is written, which is what lets the whole
 * operation be refused, and kept as a value because [ReplaceUndo] is the same shape inverted.
 *
 * @property source the text [edits] were computed against. Carried rather than re-read: the read
 *   already happened when the resource was validated, and reading again between validating and
 *   writing would reopen the window `_validateBeforePrepare` exists to close. It is also what an
 *   undo compares against - see [ReplaceUndo].
 */
data class ResourceEdits(val path: String, val cell: Int?, val source: String, val edits: List<TextEdit>)

/**
 * One replace-all, ready to be taken back - **one entry for the command, not one per file**.
 *
 * `MultiModelEditStackElement` over every model a bulk edit touched, whose `undo()` loops the lot in
 * one go (`editStack.ts:243`, `:330-336`). Each resource carries the edits that put it back -
 * [invertedAgainst], which is `TextModel._applyUndo`'s own arithmetic - and the text the replace
 * produced, which is what says the undo is still allowed.
 *
 * **And nothing of this reaches an open document's own history.** `CellUndo.adoptingRewrites`
 * exists for exactly that: a second entry there would make one command undoable twice, in two
 * scopes that could then disagree about what the document is.
 */
data class ReplaceUndo(val resources: List<ResourceEdits>)

/**
 * How an undo ended.
 *
 * Its own type rather than an arm of [ReplaceOutcome], because the two refusals quote different
 * messages: a replace refuses on `"has changed in the meantime"` (`bulkTextEdits.ts:216`) and an
 * undo on *"Could not undo '{0}' across all files because changes were made to {1}"*
 * (`undoRedoService.ts:858-869`). There is no redo here, because nothing asks for one -
 * `MultiModelEditStackElement` has one and this does not.
 */
sealed interface UndoOutcome {
  /** Every resource was put back. */
  data object Undone : UndoOutcome

  /** Nothing was put back, because [paths] no longer read the way the replace left them. */
  data class Refused(val paths: List<String>) : UndoOutcome
}

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
 * val replace = WorkspaceReplace(openDocuments, notebooks, workspace)
 * val done = replace.replaceAll(results, "dog", query, false) as ReplaceOutcome.Replaced
 * replace.undo(done.undo)      // and the workspace is exactly what it was
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
   * That distinction is the point: a replace that took a query and searched again could
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
    val written = planned.map { requireNotNull(it.second) }
    written.forEach { write(it) }
    return ReplaceOutcome.Replaced(files.sumOf { it.matches.size }, files.size, written.asUndo())
  }

  /**
   * Puts back everything one replace-all wrote, or **nothing at all**.
   *
   * `_checkWorkspaceUndo` refuses an undo that is no longer the closest past element on every
   * impacted resource (`undoRedoService.ts:853-869`), so what is compared here is the whole
   * resource and not the characters the replace put there: a file edited anywhere since is a file
   * this undo may not rewrite.
   */
  suspend fun undo(undo: ReplaceUndo): UndoOutcome {
    val stale = undo.resources.filter { sourceOf(it.resource()) != it.source }.map { it.path }
    if (stale.isNotEmpty()) return UndoOutcome.Refused(stale.distinct())
    undo.resources.forEach { write(it) }
    return UndoOutcome.Undone
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
      ?.let { ResourceEdits(resource.path, resource.cell, source, it) }
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
    val cell = edits.cell ?: return workspaceRepository.writeText(edits.path, edits.rewritten())
    val notebook = notebookRepository.readNotebook(edits.path)
    val rewritten = notebook.cells.mapIndexed { at, existing ->
      if (at == cell) existing.copy(source = edits.rewritten()) else existing
    }
    // `copy` and never a rebuild: nbformat is an open format, and `Notebook.preserved` and
    // `Cell.preserved` are what carry the fields this app does not model (`NotebookRoundTripTest`).
    notebookRepository.saveNotebook(notebook.copy(cells = rewritten))
  }
}

/** What this resource becomes - the source it was validated against, edited. */
private fun ResourceEdits.rewritten(): String = source.withEditsApplied(edits)

/** Which resource this addresses, so an undo routes exactly as the replace that made it did. */
private fun ResourceEdits.resource(): Resource = Resource(path, cell, emptyList())

/**
 * What was written, as what puts it back: each resource's edits inverted, against the text they
 * produced - see [ReplaceUndo].
 */
private fun List<ResourceEdits>.asUndo(): ReplaceUndo =
  ReplaceUndo(map { ResourceEdits(it.path, it.cell, it.rewritten(), it.edits.invertedAgainst(it.source)) })

/**
 * One file's matches split into the resources they are actually in - the file, or its cells.
 *
 * A notebook's matches each name a cell, and one cell is one resource with one text to
 * validate and rewrite; a plain file's name none, and the file is the single resource. Grouping is
 * what turns `MatchedFile` into what `ResourceTextEdit` addresses.
 */
private data class Resource(val path: String, val cell: Int?, val matches: List<MatchedLine>)

private fun MatchedFile.resources(): List<Resource> =
  matches.groupBy { it.cell }.map { (cell, matches) -> Resource(path, cell, matches) }
