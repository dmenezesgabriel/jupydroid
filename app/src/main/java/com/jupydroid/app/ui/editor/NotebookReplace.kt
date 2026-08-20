package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Changing what the find bar found - `replaceCurrentMatch` and `replaceAllMatches`, and the three
 * pieces of view state the replace row is made of.
 *
 * **Beside [NotebookSearch] rather than inside it, which detekt asked for and the reference
 * agrees with**: `ISearchProvider` declares finding and replacing as two groups
 * (`startQuery`/`highlightNext` against `replaceCurrentMatch`/`replaceAllMatches`), and the view
 * draws them as two rows. They share one [SearchState] because they are one bar; they are two
 * objects because only one of them writes to the document.
 *
 * The replacement text itself is [replacementFor], which is where the reference's two-step rule
 * lives.
 */
/**
 * `documentsearch:startWithReplace` - the same bar with its replace row shown.
 *
 * A field of the search rather than a second command, because that is what the reference's own is:
 * `startWithReplace` runs `start` and then opens the view's replace section
 * (`packages/documentsearch-extension/src/index.ts:46-48`), which is one boolean on the view.
 */
fun EditorUiState.withReplaceShown(shown: Boolean): EditorUiState =
  search?.let { copy(search = it.copy(replaceShown = shown)) } ?: this

/** The replacement box was typed into. Changes nothing that is found - only what a replace writes. */
fun EditorUiState.withReplaceText(text: String): EditorUiState =
  search?.let { copy(search = it.copy(replaceText = text)) } ?: this

/** `preserveCase` - see [replacementFor]. On the search rather than the query, as the reference has it. */
fun EditorUiState.withPreserveCase(preserve: Boolean): EditorUiState =
  search?.let { copy(search = it.copy(preserveCase = preserve)) } ?: this

/**
 * `replaceCurrentMatch` - the one match the arrows are standing on, and then on to the next.
 *
 * Three things happen in the reference and all three are here: the source is rewritten
 * (`packages/codemirror/src/searchprovider.ts:352-356`), the query is re-run over it, and the
 * cursor moves to the first match **at or after** where the replacement ended
 * (`:360-380`), looping to the top when there is none.
 *
 * Undoable, and it costs nothing to be: `recording` derives the rewritten cells from the diff and
 * bumps [EditorUiState.sourceRevisions] with them, which is what tells the cell's field its text
 * changed underneath it. `docs/DECISIONS.md` wrote that rule for Split and named this as its next
 * user.
 */
fun EditorUiState.withCurrentMatchReplaced(): EditorUiState {
  val searching = search ?: return this
  val standing = searching.standingMatchIn(this)
  return if (standing == null) this else replacing(searching, standing.first, standing.second)
}

/** The rewrite itself, split off so [withCurrentMatchReplaced] answers "is there one" and nothing else. */
private fun EditorUiState.replacing(searching: SearchState, hit: SearchHit, cell: Cell): EditorUiState {
  val replacement = replacementFor(
    matched = cell.source.substring(hit.range.first, hit.range.last + 1),
    replaceText = searching.replaceText,
    query = searching.query,
    preserveCase = searching.preserveCase,
  )
  val rewritten = cell.source.replaceRange(hit.range.first, hit.range.last + 1, replacement)
  // Where the cursor goes next: the end of what was just written, not the start of it.
  val after = hit.range.first + replacement.length
  return copy(cells = cells.map { if (it.id == cell.id) it.copy(source = rewritten) else it }, dirty = true)
    .recording(before = this)
    .withSearchRefoundFrom(cell.id, after)
}

/** The match the arrows are standing on and the cell holding it, or null when there is none. */
private fun SearchState.standingMatchIn(state: EditorUiState): Pair<SearchHit, Cell>? {
  val hit = current?.let { hits.getOrNull(it) } ?: return null
  return state.cells.firstOrNull { it.id == hit.cellId }?.let { hit to it }
}

/**
 * `replaceAllMatches` - every match in the notebook, in one operation.
 *
 * Per cell the reference folds its matches into a new source in order, keeping the text between
 * them (`packages/codemirror/src/searchprovider.ts:400-432`); across cells it simply maps over
 * every provider (`packages/notebook/src/searchprovider.ts:485-497`). One [recording] for the lot,
 * because one command is one undoable operation - the boundary `CellEdits.applying` already sets.
 *
 * Nothing is left to stand on afterwards, which needs no special case: the query is re-run and
 * finds nothing, so the counter reads `-/-` on its own.
 */
fun EditorUiState.withAllMatchesReplaced(): EditorUiState {
  val searching = search?.takeIf { it.hits.isNotEmpty() } ?: return this
  val byCell = searching.hits.groupBy { it.cellId }
  val rewritten = cells.map { cell ->
    byCell[cell.id]?.let { cell.copy(source = cell.source.withHitsReplaced(it, searching)) } ?: cell
  }
  return copy(cells = rewritten, dirty = true).recording(before = this).withSearchRecomputed()
}

/** One cell's source with every one of its matches replaced, keeping the text between them. */
private fun String.withHitsReplaced(hits: List<SearchHit>, searching: SearchState): String {
  val out = StringBuilder()
  var lastEnd = 0
  hits.forEach { hit ->
    out.append(substring(lastEnd, hit.range.first))
    out.append(
      replacementFor(
        matched = substring(hit.range.first, hit.range.last + 1),
        replaceText = searching.replaceText,
        query = searching.query,
        preserveCase = searching.preserveCase,
      ),
    )
    lastEnd = hit.range.last + 1
  }
  return out.append(substring(lastEnd)).toString()
}

/** Re-runs the query and stands on the first match at or after where a replacement ended. */
private fun EditorUiState.withSearchRefoundFrom(cellId: String, offset: Int): EditorUiState {
  val refound = withSearchRecomputed()
  val searching = refound.search ?: return refound
  val landed = searching.hits.firstHitAtOrAfter(refound, cellId, offset)
  return refound.copy(search = searching.copy(current = landed))
}

/**
 * The replace half of the find bar, driven straight from the screen - see [NotebookSearch] for its
 * other half.
 *
 * Takes the save because it is the only part of searching that writes: a replace rewrites cell
 * source, where finding one only says where it is.
 *
 * ```
 * val replace = NotebookReplace(uiState, save)
 * replace.textChanged("dog")
 * replace.current()      // the match the arrows are on
 * ```
 */
class NotebookReplace(private val uiState: MutableStateFlow<EditorUiState>, private val save: NotebookSaveDebounce) {
  /** `documentsearch:startWithReplace`, and its other half - reads the state, like every flip row. */
  fun toggleShown() = uiState.update { it.withReplaceShown(it.search?.replaceShown != true) }

  /** The replacement box was typed into. */
  fun textChanged(text: String) = uiState.update { it.withReplaceText(text) }

  /** `preserveCase` - see [replacementFor]. */
  fun togglePreserveCase() = uiState.update { it.withPreserveCase(it.search?.preserveCase != true) }

  /** `Replace` - the match the arrows are on, and then on to the next. */
  fun current() = replacing { it.withCurrentMatchReplaced() }

  /** `Replace All` - every match in the notebook, as one undoable operation. */
  fun all() = replacing { it.withAllMatchesReplaced() }

  /** Saves only when the document actually changed, the rule every writer in this package keeps. */
  private fun replacing(replace: (EditorUiState) -> EditorUiState) {
    val before = uiState.value
    val after = replace(before)
    uiState.value = after
    if (after.cells !== before.cells) save.schedule()
  }
}
