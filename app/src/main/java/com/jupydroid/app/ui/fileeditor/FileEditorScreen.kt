package com.jupydroid.app.ui.fileeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.input.InputTransformation
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.ui.editor.CodeCellField
import com.jupydroid.app.ui.editor.PythonTypingRules
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
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FileEditorViewModel =
    viewModel { FileEditorViewModel(path, workspaceRepository, applicationScope) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  // Its file was deleted, so the screen goes with it - `deleteWidgets`, asked for by the document.
  LaunchedEffect(state.gone) { if (state.gone) onBack() }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to files")
          }
        },
      )
    },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).imePadding()) {
      state.error?.let { FileEditorError(it) }
      if (state.loaded) {
        // The whole file scrolls, rather than the field: a `BasicTextField` given an unbounded
        // height lays out every line, which is what keeps one gutter numbering the whole document
        // instead of a window onto it.
        CodeCellField(
          source = state.text,
          onSourceEdited = viewModel::edit,
          highlighter = state.syntax,
          typing = remember(state.name) { typingRulesFor(state.name) },
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
 */
private fun typingRulesFor(fileName: String): InputTransformation =
  if (fileName.substringAfterLast('.', "").lowercase() in BRACKETED) {
    PythonTypingRules()
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
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  )
}
