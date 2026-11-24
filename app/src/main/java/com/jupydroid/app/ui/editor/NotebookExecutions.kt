package com.jupydroid.app.ui.editor

import com.jupydroid.app.DocumentOpening
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * executions.retain(key, kernel, debugger, uiState, scope, followJob)
 * executions.adopt(key)  // the run a reopening editor takes over - null once it has drained
 * executions.at(key)     // the same holder, for a test that only inspects it
 * ```
 */
class NotebookExecutions {
  private val retained = ConcurrentHashMap<NotebookExecutionKey, NotebookExecution>()

  /**
   * Hands a detached run to the registry and arms its drain watch. The conditional remove in
   * [NotebookExecution]'s own `onDrained` is what keeps a holder replaced under the same key
   * from evicting its successor when its own run finally ends - a gone document's holder can
   * share the key a recreated file's run retains under, and both still owe their own kernel
   * its close.
   */
  fun retain(
    key: NotebookExecutionKey,
    kernel: NotebookKernel,
    debugger: NotebookDebugger,
    uiState: MutableStateFlow<EditorUiState>,
    scope: CoroutineScope,
    followJob: Job?,
  ): NotebookExecution {
    val execution =
      NotebookExecution(key, kernel, debugger, uiState, scope, followJob) { drained ->
        retained.remove(key, drained)
      }
    retained[key] = execution
    execution.watchDetached()
    return execution
  }

  /**
   * Takes the run retained for the document [key] opens out of the registry and stops its
   * drain watch - the screen that asked owns the kernel from here, so a drain it never sees
   * must not close it underneath.
   *
   * Matched by where the document lives rather than by the key alone: the key keeps the path
   * the tab opened under, so a file renamed while its run was detached is found under its new
   * name, and a file recreated at the old name - or the gone document itself - is not. See
   * [NotebookExecution.speaksFor].
   */
  fun adopt(key: NotebookExecutionKey): NotebookExecution? {
    val execution =
      retained.values
        .filter { it.speaksFor(key) }
        .minByOrNull { it.key.opening.ordinal }
    if (execution == null || !retained.remove(execution.key, execution)) return null
    execution.stopWatching()
    return execution
  }

  /** The run retained for [key], or null - none was handed over, or it has already drained. */
  fun at(key: NotebookExecutionKey): NotebookExecution? = retained[key]
}
