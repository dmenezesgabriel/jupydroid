package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.CellOutputs
import com.jupydroid.app.ui.editor.codeSourceStyle
import com.jupydroid.app.ui.editor.executionLabel

/**
 * P6b-3: the Console's read-only transcript - one row per submitted [ConsoleEntry], reusing the
 * notebook editor's own prompt ([executionLabel]) and output rendering ([CellOutputs]) exactly,
 * per [ConsoleEntry]'s own doc comment and JupyterLab's `CodeConsole`, which has no dedicated
 * transcript-cell type either - it reuses a full `CodeCell` per entry.
 *
 * `executionLabel`'s `count` is the entry's own position in [entries] (`index + 1`), since
 * [ConsoleEntry] carries no execution-count field yet - P6b-4's kernel wiring is what supplies a
 * real one, once a `ConsoleViewModel` exists to hold it.
 *
 * ```kotlin
 * ConsoleTranscript(listOf(ConsoleEntry("1 + 1", listOf(CellOutput.Stream(STDOUT, "2\n")), STOPPED_OK)))
 * ```
 */
@Composable
fun ConsoleTranscript(entries: List<ConsoleEntry>, modifier: Modifier = Modifier) {
  LazyColumn(modifier = modifier) {
    itemsIndexed(entries) { index, entry -> ConsoleTranscriptEntry(entry, index + 1) }
  }
}

@Composable
private fun ConsoleTranscriptEntry(entry: ConsoleEntry, count: Int) {
  Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
    executionLabel(CellKind.CODE, entry.status, count)?.let { prompt ->
      Text(text = prompt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
    Text(text = entry.input, style = codeSourceStyle())
    CellOutputs(outputs = entry.outputs, hidden = false, scrolled = false, search = emptyList(), onExpand = {})
  }
}
