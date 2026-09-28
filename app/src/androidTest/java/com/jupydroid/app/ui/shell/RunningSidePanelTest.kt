package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.FileEditor
import com.jupydroid.app.RunningTab
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `jp-running-sessions` - the reference's "Sessions and Tabs" sidebar
 * (`packages/running-extension/src/index.ts:121`), on this app's one side area.
 *
 * Three sections in the reference's own order - Open Tabs, Kernel Sessions, Terminals - each a
 * collapsible `Section` whose toolbar carries the bulk action (`running/src/index.tsx`'s
 * `shutdown-all` button). The rows are the managers' `running()` lists, which here are the
 * kernel/terminal ports and the shell's own tab order.
 */
@RunWith(AndroidJUnit4::class)
class RunningSidePanelTest {
  @get:Rule val compose = createComposeRule()

  private val registry = KernelSessionRegistry()
  private val kernelGateway = FakeKernelGateway(registry)
  private val kernelSessions = FakeKernelSessions(registry)
  private val terminalGateway = FakeTerminalGateway()
  private lateinit var viewModel: RunningPanelViewModel

  private val activated = mutableListOf<NavKey>()
  private val closed = mutableListOf<NavKey>()
  private val opened = mutableListOf<KernelSessionModel>()
  private val reattached = mutableListOf<TerminalSession>()

  /** The panel's own chrome: the three section names and the toolbar's refresh. */
  @Test
  fun theThreeSectionsAndTheRefreshAreDrawn() {
    show()

    compose.onNodeWithText("Sessions and Tabs").assertIsDisplayed()
    compose.onNodeWithText("Open Tabs").assertIsDisplayed()
    compose.onNodeWithText("Kernel Sessions").assertIsDisplayed()
    compose.onNodeWithText("Terminals").assertIsDisplayed()
    compose.onNodeWithContentDescription("Refresh list").assertIsDisplayed()
  }

  /** `opentabs.ts`'s `OpenTab.open()` - `labShell.activateById` for the tapped widget. */
  @Test
  fun anOpenTabRowActivatesItsDocument() {
    show(tabs = listOf(RunningTab(Editor("a.ipynb"), "a.ipynb")))

    compose.onNodeWithText("a.ipynb", substring = true).performClick()

    assertEquals(listOf<NavKey>(Editor("a.ipynb")), activated)
  }

  /**
   * `OpenTab.shutdown()` - `widget.close()`. Routed to the caller's gate rather than run here,
   * because the dirty ask belongs to whichever document the row names.
   */
  @Test
  fun anOpenTabRowClosesThroughTheSharedGate() {
    val tab = RunningTab(Editor("a.ipynb"), "a.ipynb")
    show(tabs = listOf(tab))

    compose.onNodeWithContentDescription("Close: a.ipynb").performClick()

    assertEquals(listOf(tab.key), closed)
  }

  /** `running:kernel-open-session` - `docmanager:open` on the session's path. */
  @Test
  fun aKernelSessionRowOpensItsDocument() {
    runBlocking { kernelGateway.connect(BASE_SPACE, "dir/notes.ipynb") }
    show()
    awaitSessions(1)

    compose.onNodeWithText("notes.ipynb", substring = true).performClick()

    assertEquals("dir/notes.ipynb", opened.single().path)
  }

  /**
   * `running:kernel-shut-down` on the row's own ✕ - `DELETE api/sessions/<id>`, no confirmation,
   * which is the reference's own per-row shape (`running/src/index.tsx`'s `Item.shutdown`).
   */
  @Test
  fun aKernelSessionRowsShutdownEndsItWithoutAsking() {
    runBlocking { kernelGateway.connect(BASE_SPACE, "notes.ipynb") }
    show()
    awaitSessions(1)

    compose.onNodeWithContentDescription("Shut Down Kernel: notes.ipynb").performClick()
    compose.waitForIdle()

    assertTrue(runBlocking { kernelSessions.running().isEmpty() })
    compose.onNodeWithText("notes.ipynb", substring = true).assertDoesNotExist()
  }

  /**
   * The section toolbar's `shutdown-all`, counted the way `shutdownAllConfirmationText` counts
   * kernels (`kernels.tsx`) - here over sessions, which is what the rows are.
   */
  @Test
  fun shutDownAllKernelSessionsAsksWithTheCount() {
    runBlocking {
      kernelGateway.connect(BASE_SPACE, "a.ipynb")
      kernelGateway.connect(BASE_SPACE, "b.ipynb")
    }
    show()
    awaitSessions(2)

    compose.onNodeWithContentDescription("Shut Down All: Kernel Sessions").performClick()

    compose.onNodeWithText("Shut Down All?").assertIsDisplayed()
    compose.onNodeWithText(
      "Are you sure you want to permanently shut down the 2 running kernel sessions?",
    ).assertIsDisplayed()
    compose.onNodeWithText("a.ipynb", substring = true).assertIsDisplayed()

    compose.onNodeWithContentDescription("Confirm Shut Down All").performClick()
    compose.waitForIdle()

    assertTrue(runBlocking { kernelSessions.running().isEmpty() })
  }

  /** The same confirm, singular - `trans._n`'s one-kernel phrasing. */
  @Test
  fun shutDownAllOverOneSessionUsesTheSingular() {
    runBlocking { kernelGateway.connect(BASE_SPACE, "a.ipynb") }
    show()
    awaitSessions(1)

    compose.onNodeWithContentDescription("Shut Down All: Kernel Sessions").performClick()

    compose.onNodeWithText(
      "Are you sure you want to permanently shut down the running kernel session?",
    ).assertIsDisplayed()
  }

  /** `terminal:open` - the row reattaches to the shell the server still holds. */
  @Test
  fun aTerminalRowReattachesToItsShell() {
    runBlocking { terminalGateway.open(cwd = null) }
    show()
    awaitTerminals(1)

    compose.onNodeWithText("terminals/1").performClick()

    assertEquals(listOf(TerminalSession("1")), reattached)
  }

  /** `manager.shutdown(name)` - `DELETE api/terminals/<name>` on the row's own control. */
  @Test
  fun aTerminalRowsShutdownEndsItWithoutAsking() {
    runBlocking { terminalGateway.open(cwd = null) }
    show()
    awaitTerminals(1)

    compose.onNodeWithContentDescription("Shut Down: terminals/1").performClick()
    compose.waitForIdle()

    assertTrue(runBlocking { terminalGateway.running().isEmpty() })
  }

  /** `manager.shutdownAll()` behind the section's own wording (`terminal-extension/index.ts:458`). */
  @Test
  fun shutDownAllTerminalsAsksFirst() {
    runBlocking { terminalGateway.open(cwd = null) }
    show()
    awaitTerminals(1)

    compose.onNodeWithContentDescription("Shut Down All: Terminals").performClick()

    compose.onNodeWithText("Shut Down All?").assertIsDisplayed()
    compose.onNodeWithText(
      "Are you sure you want to permanently shut down all running terminals?",
    ).assertIsDisplayed()

    compose.onNodeWithContentDescription("Confirm Shut Down All").performClick()
    compose.waitForIdle()

    assertTrue(runBlocking { terminalGateway.running().isEmpty() })
  }

  /**
   * The Open Tabs toolbar's `shutdown-all` - `Close All` with the reference's own wording
   * (`opentabs.ts:93-97`), then one close per tab through the shared gate.
   */
  @Test
  fun closeAllTabsAsksThenClosesEach() {
    val tabs = listOf(RunningTab(Editor("a.ipynb"), "a.ipynb"), RunningTab(FileEditor("b.py"), "b.py"))
    show(tabs = tabs)

    compose.onNodeWithContentDescription("Close All: Open Tabs").performClick()

    compose.onNodeWithText("Close All?").assertIsDisplayed()
    compose.onNodeWithText("Are you sure you want to close all open tabs?").assertIsDisplayed()

    compose.onNodeWithContentDescription("Confirm Close All").performClick()

    assertEquals(tabs.map { it.key }, closed)
  }

  /** `jp-mod-disabled` - a section with nothing in it offers no bulk action to confirm. */
  @Test
  fun aSectionsBulkActionIsDisabledWhileItIsEmpty() {
    show()

    compose.onNodeWithContentDescription("Shut Down All: Kernel Sessions").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Shut Down All: Terminals").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Close All: Open Tabs").assertIsNotEnabled()
  }

  /** The accordion's own toggle - the section name is the handle, rows go with the fold. */
  @Test
  fun tappingASectionHeaderCollapsesItsRows() {
    runBlocking { kernelGateway.connect(BASE_SPACE, "notes.ipynb") }
    show()
    awaitSessions(1)

    compose.onNodeWithText("Kernel Sessions").performClick()
    compose.onNodeWithText("notes.ipynb", substring = true).assertDoesNotExist()

    compose.onNodeWithText("Kernel Sessions").performClick()
    compose.onNodeWithText("notes.ipynb", substring = true).assertIsDisplayed()
  }

  /** `refresh`'s toolbar button - `managers.items().forEach { it.refreshRunning() }`. */
  @Test
  fun theRefreshButtonAsksTheServerAgain() {
    show()

    runBlocking { kernelGateway.connect(BASE_SPACE, "late.ipynb") }
    compose.onNodeWithContentDescription("Refresh list").performClick()
    compose.waitForIdle()

    compose.onNodeWithText("late.ipynb", substring = true).assertIsDisplayed()
  }

  private fun show(tabs: List<RunningTab> = emptyList()) {
    viewModel = RunningPanelViewModel(kernelSessions, terminalGateway)
    compose.setContent {
      JupyterDroidTheme {
        RunningSidePanel(
          kernelSessions = kernelSessions,
          terminalGateway = terminalGateway,
          openTabs = tabs,
          onActivateTab = { activated += it },
          onCloseTab = { closed += it },
          onOpenSession = { opened += it },
          onOpenTerminal = { reattached += it },
          viewModel = viewModel,
        )
      }
    }
  }

  /** The first poll tick has to land before rows exist - `refresh()` is a suspend behind Main. */
  private fun awaitSessions(count: Int) =
    compose.waitUntil(AWAIT_TIMEOUT) { viewModel.uiState.value.sessions.size == count }

  private fun awaitTerminals(count: Int) =
    compose.waitUntil(AWAIT_TIMEOUT) { viewModel.uiState.value.terminals.size == count }

  private companion object {
    const val AWAIT_TIMEOUT = 5_000L
  }
}
