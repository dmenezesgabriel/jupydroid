package com.jupydroid.app.ui.fileeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.rangeIn
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.Breadcrumb
import com.jupydroid.app.ui.ROOT_LABEL
import com.jupydroid.app.ui.SaveBeforeCloseGate
import com.jupydroid.app.ui.ancestorCrumbsFor
import com.jupydroid.app.ui.editor.CodeCellField
import com.jupydroid.app.ui.editor.PythonTypingRules
import com.jupydroid.app.ui.editor.adoptRewrittenSource
import kotlinx.coroutines.CoroutineScope

/**
 * A file that is not a notebook, in one editor.
 *
 * The reference opens anything its notebook factory does not claim in a single CodeMirror over
 * the whole document (`packages/fileeditor`). This is the *cell* editor's own field doing the
 * same job, which is the point: line numbers, syntax highlighting, bracket matching and the IME
 * symbol row are the same code as in a code cell, not a second implementation that will drift
 * from it.
 *
 * **There is no in-file find.** `fileeditor:find` is `editor.execCommand(findNext)` over a
 * CodeMirror search panel (`fileeditor-extension/src/commands.ts:624-641`), mounted by the
 * `searchWithCM` editorConfig key (`packages/codemirror/src/factory.ts:28`), and the
 * documentsearch overlay's own answer is `jp-fileeditorSearchProvider`
 * (`fileeditor-extension/src/index.ts:288`). None of the three exists here: a field keeps its
 * own selections rather than mounting a search panel, and finding is the workspace search's,
 * whose results this screen lands on through [reveal].
 *
 * ```
 * FileEditorScreen("reports/data.csv", workspaceRepository, applicationScope, onBack = { … })
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(
  path: String,
  workspaceRepository: WorkspaceRepository,
  applicationScope: CoroutineScope,
  /**
   * `fileeditor:toggle-autoclosing-brackets-universal` - the same setting the notebook's cells
   * read, and the toggle for it lives on the notebook's View menu since this screen has no top
   * bar of its own to put a second one on.
   */
  editorSettingsRepository: EditorSettingsRepository,
  /**
   * `fileeditor:close`'s effect - pops this entry. The gate below asks first when dirty; who
   * draws the affordance is the tab strip's own business (✕, or the arrow below two panes).
   */
  onBack: () -> Unit,
  /**
   * A breadcrumb segment was tapped: browse that folder (`""` is the workspace root).
   *
   * The same door the notebook has (`EditorScreen.onGoToFolder`), wired to the same lambda in
   * `Navigation.kt` rather than a second copy of it - the browser is a different `NavEntry`, so
   * which half of the shell has to move differs by layout and neither document should know that.
   */
  onGoToFolder: (String) -> Unit = {},
  /**
   * A search result this screen was opened from, to be landed on - `IEditorOptions.selection`,
   * which `searchView.open` builds from the match's own range (`searchView.ts:2240-2256`).
   *
   * Null for every other way of opening a file, which is every way but a search result.
   */
  reveal: MatchedLine? = null,
  /** Consumed once, the way `browseRequest` is - see the `LaunchedEffect` below. */
  onRevealHandled: () -> Unit = {},
  /**
   * This file moved - `EditorScreen.onNotebookMoved`'s counterpart, since the key keeps the path
   * it was opened with and a rename moves the file underneath it. Reported up rather than into
   * the key, so the tab strip and the browser's open-row marking can read where it lives now.
   */
  onFileMoved: (String) -> Unit = {},
  /**
   * Where this document says it is open, so a workspace replace edits the model rather than the
   * file. Defaulted so a test driving this screen alone need not supply one.
   */
  openDocuments: OpenDocuments = remember { OpenDocuments() },
  modifier: Modifier = Modifier,
  viewModel: FileEditorViewModel =
    viewModel {
      FileEditorViewModel(path, workspaceRepository, applicationScope, openDocuments, editorSettingsRepository)
    },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val editorSettings by editorSettingsRepository.observeSettings()
    .collectAsStateWithLifecycle(EditorSettings())

  // `saveBeforeClose` (`docmanager/src/dialogs.ts:155`): a dirty document asks before it leaves,
  // whichever way out was taken - the gate's own system-Back handler, or the tab strip's gated
  // close. The lambda it hands back has no consumer now that the dismiss lives on the strip; the
  // call stays for the handler. The `gone` path stays outside the gate: a deleted file has
  // nothing a save could put back.
  SaveBeforeCloseGate(
    dirty = state.dirty,
    backAsks = state.dirty,
    title = state.name,
    saveNow = viewModel::saveNow,
    discardChanges = viewModel::discardChanges,
    onClose = onBack,
  )

  // Its file was deleted, so the screen goes with it - `deleteWidgets`, asked for by the document.
  LaunchedEffect(state.gone) { if (state.gone) onBack() }

  // `Context._updatePath` reported up: the key cannot carry a rename, so the map beside the stack
  // does - the same effect `state.notebookId`'s is for the notebook editor.
  LaunchedEffect(state.path) { onFileMoved(state.path) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    // **The page a document is drawn on, stated rather than inherited.** `Scaffold`'s default is
    // `colorScheme.background`, which this app maps onto `editor.background` - the right colour
    // by way of a role that could be re-mapped, and `ColorSchemeTest` asserts only that a role
    // is *somewhere* on the ramp, never which position. VS Code separates a part's identity from
    // a surface's elevation, and this is the document's own id (ADR-0042).
    containerColor = editorPalette.editorBackground,
    topBar = {
      // **The trail is all this screen's top carries** - `BreadcrumbsControl`'s own spot under a
      // tab title (`parts/editor/breadcrumbsControl.ts`), and the tab is what carries the name
      // and the close now, so there is no action row above it at all. Ancestors only, and
      // **absent** rather than blank at the root - both departures argued in
      // [ancestorCrumbsFor]. The status-bar insets are already consumed by the tab strip above
      // this pane (`MainAreaTabStrip`), so a crumbs-only Column needs none of its own.
      Column(modifier = Modifier.background(TopAppBarDefaults.topAppBarColors().containerColor)) {
        val crumbs = ancestorCrumbsFor(path, ROOT_LABEL)
        if (crumbs.isNotEmpty()) {
          Breadcrumb(crumbs = crumbs, onGoTo = onGoToFolder)
        }
      }
    },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).imePadding()) {
      state.error?.let { FileEditorError(it) }
      if (state.loaded) {
        // The whole file scrolls, rather than the field: a `BasicTextField` given an unbounded
        // height lays out every line, which is what keeps one gutter numbering the whole document
        // instead of a window onto it.
        // **The field's state is remembered against the text it was built from**, because landing on
        // a match means editing that state and the file arrives after the first composition.
        val source = rememberTextFieldState(state.text)
        // **What tells the field its text was rewritten by something other than typing.**
        // `rememberTextFieldState(initialText)` has no keys (foundation `TextFieldState.kt:657-661`),
        // so a changed `state.text` alone leaves the field showing what it had - measured, and it is
        // why `FileEditorUiState.revision` exists. The same function the notebook's cells use.
        adoptRewrittenSource(source, state.text, state.revision)
        val scroll = rememberScrollState()
        // How the field laid the text out, which is the only thing that knows where a line ended up
        // once wrapping has had its say - see `CodeCellField.onLayoutMeasured`.
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        // `selection` handed to `openEditor` (`searchView.ts:2245-2256`), applied once the file is
        // here **and** laid out: selecting is half of landing on a match and scrolling to it is the
        // other, and only the layout can say where to scroll to. A match the file no longer holds
        // does neither, rather than selecting the wrong characters (`MatchedLine.rangeIn`) - and
        // either way the request is reported handled, so a later recomposition cannot drag the
        // reader back to a match they have scrolled away from.
        // Once per request, whatever the field does afterwards: `onLayoutMeasured` fires again on
        // any re-measure, and without this the effect would re-run and report the same match handled
        // twice - measured, at `expected:<1> but was:<2>`.
        var landedOn by remember { mutableStateOf<MatchedLine?>(null) }
        LaunchedEffect(reveal, state.text, layout) {
          val measured = layout ?: return@LaunchedEffect
          val landing = reveal?.takeIf { it != landedOn } ?: return@LaunchedEffect
          landedOn = landing
          landing.rangeIn(state.text)?.let { found ->
            source.edit { selection = TextRange(found.first, found.last + 1) }
            scroll.scrollTo(measured.topOf(found.first))
          }
          onRevealHandled()
        }
        CodeCellField(
          state = source,
          onSourceEdited = viewModel::edit,
          highlighter = state.syntax,
          typing = remember(state.name, editorSettings.autoClosingBrackets, editorSettings.indentUnit) {
            typingRulesFor(state.name, editorSettings.autoClosingBrackets, editorSettings.indentUnit)
          },
          wrapEnabled = editorSettings.codeWrapEnabled,
          onLayoutMeasured = { layout = it },
          modifier = Modifier.fillMaxSize().verticalScroll(scroll),
        )
      }
    }
  }
}

/**
 * Auto-closing brackets where brackets are structure, and nothing where they are punctuation.
 *
 * `PythonTypingRules` closes a pair as you open it, which is right in a `.py` or a `.json` and
 * wrong in prose - a `(` in a sentence is not the start of anything.
 *
 * [autoClosingBrackets] still reaches a bracketed file - `notebook:toggle-autoclosing-brackets`,
 * universal - but never a prose one: indentation carries on either way, and there is nothing
 * bracket-related in prose for the setting to have an opinion about.
 */
private fun typingRulesFor(
  fileName: String,
  autoClosingBrackets: Boolean,
  indentUnit: IndentUnit,
): InputTransformation = if (fileName.substringAfterLast('.', "").lowercase() in BRACKETED) {
  PythonTypingRules(autoClosingBrackets, indentUnit)
} else {
  InputTransformation { }
}

private val BRACKETED = setOf("py", "json", "ipynb")

/** A shared workspace can lose a file under the user, so this is an event, not a crash. */
@Composable
private fun FileEditorError(message: String) {
  Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
  )
}

/**
 * The top of the line [offset] sits on, in pixels from the top of the text.
 *
 * `revealRangeInCenterIfOutsideViewport`'s job done plainly: the reference centres a revealed range
 * in the viewport, and this puts it at the top. Centring needs the viewport's height, which the
 * scroll state knows and the layout does not - and the top of the file above a match is the context
 * a reader wants anyway, where centring would spend half the screen on what came before it.
 */
private fun TextLayoutResult.topOf(offset: Int): Int = getLineTop(getLineForOffset(offset)).toInt()
