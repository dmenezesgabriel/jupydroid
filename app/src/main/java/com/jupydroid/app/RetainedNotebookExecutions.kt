package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.ui.editor.NotebookExecutions

/**
 * The [NotebookExecutions] registry kept for exactly as long as the activity that can adopt
 * from it.
 *
 * A notebook's `EditorViewModel` lives in its `NavEntry`'s store, which `leaveWorkspace` and a
 * tab's close both drop - and the run it was collecting would die with it. The holder the run
 * is handed to instead has to outlive *any* entry's store but not the process. An activity
 * `ViewModel` is exactly that lifetime - the same call
 * [RetainedOpenDocuments] and [RetainedConsole] already make for the same reason.
 */
class RetainedNotebookExecutions : ViewModel() {
  val executions = NotebookExecutions()
}

/**
 * The executions registry the activity already holds, or a new one on its first composition.
 *
 * ```
 * MainNavigation(…, notebookExecutions = rememberRetainedNotebookExecutions())
 * ```
 */
@Composable
fun rememberRetainedNotebookExecutions(): NotebookExecutions = viewModel { RetainedNotebookExecutions() }.executions
