package com.jupydroid.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupydroid.app.domain.model.LicenceDocument
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.editor.RichText
import com.jupydroid.app.ui.editor.codeTextStyle

/** [LicencesScreen]'s own root, so a test can find it without depending on its title text. */
const val LICENCES_SCREEN_TAG = "licences_screen"

/** The tag of the row that opens the document titled [title]. */
fun licenceRowTag(title: String): String = "licence_row_$title"

/** The tag of the reader showing one document's text. */
const val LICENCE_READER_TAG = "licence_reader"

/**
 * The Licences screen: the documents the APK ships as a list, and the one tapped as text.
 *
 * Back from a document returns to the list through a [BackHandler], as the explorer does for a
 * folder; back from the list is the tab strip's, as for Settings.
 *
 * ```
 * LicencesScreen(viewModel { LicencesViewModel(appContainer.licenceDocuments) })
 * ```
 */
@Composable
fun LicencesScreen(viewModel: LicencesViewModel, modifier: Modifier = Modifier) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val onOpen = viewModel::open
  BackHandler(enabled = (state as? LicencesUiState.Listed)?.reading != null) { viewModel.close() }
  Scaffold(
    modifier = modifier.fillMaxSize().testTag(LICENCES_SCREEN_TAG),
    containerColor = editorPalette.editorBackground,
  ) { padding ->
    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
      when (val shown = state) {
        LicencesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        is LicencesUiState.Failed -> Text(shown.message, Modifier.padding(Spacing.lg))
        is LicencesUiState.Listed -> shown.reading?.let { LicenceReader(it) } ?: LicenceList(shown.documents, onOpen)
      }
    }
  }
}

@Composable
private fun LicenceList(documents: List<LicenceDocument>, onOpen: (String) -> Unit) {
  Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
    documents.forEach { document ->
      ListItem(
        headlineContent = { Text(document.title) },
        modifier = Modifier.testTag(licenceRowTag(document.title)).clickable { onOpen(document.title) },
      )
    }
  }
}

@Composable
private fun LicenceReader(document: LicenceDocument) {
  Column(
    modifier = Modifier.testTag(LICENCE_READER_TAG).verticalScroll(rememberScrollState()).padding(Spacing.lg),
  ) {
    if (document.isMarkdown) {
      // One parse per document, not per recomposition: THIRD_PARTY.md alone is hundreds of rows.
      RichText(remember(document.text) { MarkdownDocument.parse(document.text) })
    } else {
      Text(document.text, style = codeTextStyle())
    }
  }
}
