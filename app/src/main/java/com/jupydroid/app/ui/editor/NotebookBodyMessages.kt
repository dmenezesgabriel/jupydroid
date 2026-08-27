package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.Spacing

/**
 * What the notebook's body says when it is not drawing cells, and the one thing it says **over**
 * them.
 *
 * Split out of `EditorScreen.kt` when P2x-5b's banner took that file past the 500-line gate, and
 * along a real seam rather than an arbitrary one: these three are the document area's *messages*,
 * and none of them knows anything about a cell, a kernel or a toolbar.
 *
 * The distinction between them is load-bearing and is `EditorUiState`'s: [ErrorMessage] and
 * [EmptyMessage] replace the document, and [SaveFailureBanner] sits above it.
 */

@Composable
internal fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
    Text(
      text = message,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyLarge,
    )
  }
}

@Composable
internal fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * *"This notebook is not being saved"*, over the document rather than in place of it.
 *
 * The reference puts a failed save in a dialog (`Context.save()` -> `showErrorMessage`), which is a
 * decision nobody has to make; this is the same information without a button to press. A banner and
 * not the snackbar an export's outcome gets, for [EditorUiState.saveFailure]'s reason - the export
 * happened and is over, and this goes on being true until a save works.
 *
 * The repository's own sentence is what says *why*, exactly as a refused export shows the server's.
 */
@Composable
internal fun SaveFailureBanner(reason: String) {
  Text(
    text = "$COULD_NOT_SAVE $reason",
    color = MaterialTheme.colorScheme.error,
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.errorContainer)
      .padding(horizontal = Spacing.md, vertical = Spacing.sm),
  )
}

/** Named so a test and the banner agree on one string. */
internal const val COULD_NOT_SAVE = "Could not save this notebook:"
