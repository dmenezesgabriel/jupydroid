package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.KernelSessions
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * A notebook's session follows the notebook's rename - `_updatePath` sends `setPath` and `setName`
 * (jupyterlab@7f0e6906 `packages/docregistry/src/context.ts`), so the Running panel's row names
 * the file as it is now and opening that row reaches the renamed tab.
 */
class NotebookSessionRenameTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aRenamedNotebooksSessionIsListedUnderItsNewPathAndName() = runTest {
    val registry = KernelSessionRegistry()
    val kernelSessions = FakeKernelSessions(registry)
    val workspace = AnnouncingWorkspaceFiles()
    val viewModel = loadedEditor(
      gateway = FakeKernelGateway(registry),
      kernelSessions = kernelSessions,
      files = workspace,
      notebookId = "a.ipynb",
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    val before = kernelSessions.running().single()

    workspace.announce("a.ipynb", "g/b.ipynb")
    advanceUntilIdle()

    val after = kernelSessions.running().single()
    assertEquals("the same session, not a new one", before.serverSessionId, after.serverSessionId)
    assertEquals("g/b.ipynb", after.path)
    assertEquals("b.ipynb", after.name)
  }

  /** A folder renamed above the notebook moves it too, and the session follows the same way. */
  @Test
  fun aFolderRenamedAboveTheNotebookMovesItsSession() = runTest {
    val registry = KernelSessionRegistry()
    val kernelSessions = FakeKernelSessions(registry)
    val workspace = AnnouncingWorkspaceFiles()
    val viewModel = loadedEditor(
      gateway = FakeKernelGateway(registry),
      kernelSessions = kernelSessions,
      files = workspace,
      notebookId = "f/a.ipynb",
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    workspace.announce("f", "g")
    advanceUntilIdle()

    assertEquals("g/a.ipynb", kernelSessions.running().single().path)
  }

  /**
   * The reference sends its PATCH with `void` and never awaits it: a refusal - the server culled
   * the session a moment before - must not stop the notebook taking its new name.
   */
  @Test
  fun aRefusedSessionMoveStillLetsTheNotebookTakeItsNewName() = runTest {
    val registry = KernelSessionRegistry()
    val workspace = AnnouncingWorkspaceFiles()
    val viewModel = loadedEditor(
      gateway = FakeKernelGateway(registry),
      kernelSessions = RefusingToMoveKernelSessions(FakeKernelSessions(registry)),
      files = workspace,
      notebookId = "a.ipynb",
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    workspace.announce("a.ipynb", "b.ipynb")
    advanceUntilIdle()

    assertEquals("b.ipynb", viewModel.uiState.value.notebookId)
  }

  /** Lists and ends like the fake it wraps, and refuses every move the way a 404 PATCH does. */
  private class RefusingToMoveKernelSessions(listing: KernelSessions) : KernelSessions by listing {
    override suspend fun setPath(serverSessionId: String, documentPath: String) =
      throw IOException("PATCH api/sessions/$serverSessionId failed: HTTP 404")
  }
}
