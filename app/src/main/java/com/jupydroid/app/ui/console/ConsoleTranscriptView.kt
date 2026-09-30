package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * The Console's read-only transcript - one row per submitted [ConsoleEntry], reusing the
 * notebook editor's own prompt ([executionLabel]) and output rendering ([CellOutputs]) exactly,
 * per [ConsoleEntry]'s own doc comment and JupyterLab's `CodeConsole`, which has no dedicated
 * transcript-cell type either - it reuses a full `CodeCell` per entry.
 *
 * `executionLabel`'s count is [ConsoleEntry.executionCount] - the kernel's own count, not the
 * entry's position in [entries].
 *
 * ```kotlin
 * ConsoleTranscript(listOf(ConsoleEntry("e1", "1 + 1", listOf(CellOutput.Stream(STDOUT, "2\n")), STOPPED_OK, 1)))
 * ```
 */
@Composable
fun ConsoleTranscript(entries: List<ConsoleEntry>, modifier: Modifier = Modifier) {
  LazyColumn(modifier = modifier) {
    items(entries, key = ConsoleEntry::id) { entry -> ConsoleTranscriptEntry(entry) }
  }
}

@Composable
private fun ConsoleTranscriptEntry(entry: ConsoleEntry) {
  Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
    executionLabel(CellKind.CODE, entry.status, entry.executionCount)?.let { prompt ->
      Text(text = prompt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
    Text(text = entry.input, style = codeSourceStyle())
    CellOutputs(outputs = entry.outputs, hidden = false, scrolled = false, search = emptyList(), onExpand = {})
  }
}
