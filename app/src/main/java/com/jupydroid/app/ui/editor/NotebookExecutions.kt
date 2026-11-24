package com.jupydroid.app.ui.editor

import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.domain.model.ActiveCellRuns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Which open document a retained run belongs to: the Space whose server runs the kernel, and
 * the tab's opening - [DocumentOpening] rather than the live path, because it is the one
 * address that survives the document's own rename (`OpenDocuments.openedAs`'s rule).
 */
data class NotebookExecutionKey(val spaceId: String, val opening: DocumentOpening)

/**
 * The runs that outlived their screens, keyed by the document opening they belong to.
 *
 * `Context`+`SessionContext`'s analogue for the case the reference never built: JupyterLab's
 * widget is disposed on close and its execution state goes with it
 * (`packages/apputils/src/sessioncontext.tsx:679-709`), while ours hands the kernel and its
 * state flow here so a reopening screen can find them again. Activity-retained through
 * [com.jupydroid.app.RetainedNotebookExecutions] - outliving any one `NavEntry`'s
 * `ViewModelStore`, and not the process, whose kernels die with it anyway.
 *
 * ```
 * executions.retain(key, kernel, uiState, activeCellRuns, applicationScope)
 * executions.at(key)   // what a reopening editor adopts - null once the run has drained
 * ```
 */
class NotebookExecutions {
  private val retained = ConcurrentHashMap<NotebookExecutionKey, NotebookExecution>()

  /**
   * Hands a detached run to the registry and arms its drain watch. The conditional remove in
   * [NotebookExecution]'s own `onDrained` is what keeps a holder replaced under the same key
   * from evicting its successor when its own run finally ends.
   */
  fun retain(
    key: NotebookExecutionKey,
    kernel: NotebookKernel,
    uiState: MutableStateFlow<EditorUiState>,
    activeCellRuns: ActiveCellRuns,
    scope: CoroutineScope,
  ): NotebookExecution {
    val execution =
      NotebookExecution(key, kernel, uiState, activeCellRuns, scope) { drained -> retained.remove(key, drained) }
    retained[key] = execution
    execution.watchDetached()
    return execution
  }

  /** The run retained for [key], or null - none was handed over, or it has already drained. */
  fun at(key: NotebookExecutionKey): NotebookExecution? = retained[key]
}
