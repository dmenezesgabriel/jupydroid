package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.RecordingNotebookCells
import com.jupydroid.app.RecordingWorkspaceFileOpener
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeSpaceEnvironmentCreation
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeSpaceKernelRegistration
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.theme.ChromePalette
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.environments.EnvironmentsPanelViewModel
import com.jupydroid.app.ui.environments.EnvironmentsSidePanel
import com.jupydroid.app.ui.environments.NO_ENVIRONMENTS
import com.jupydroid.app.ui.textLayout
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.SidePanelHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What colour a side panel's text is drawn in when the text states none, read off the layout each
 * `Text` measured.
 *
 * Every panel is a `Scaffold(containerColor = sideBarBackground)`, which derives its content colour
 * with `contentColorFor`, and that matches a container to a Material role *by hex*: dark's
 * `#191A1B` is also `surfaceVariant`, so unstated text arrived as `onSurfaceVariant`, the secondary
 * colour. VS Code sets `sideBar.foreground` on the side bar's container once and every child
 * inherits it (`vscode@3f3380c6 src/vs/workbench/browser/parts/sidebar/sidebarPart.ts:173`). A JVM
 * test cannot see this: the colour is what a composition resolved
 * ([ExplorerRowColourTest][com.jupydroid.app.ui.explorer.ExplorerRowColourTest] holds the Files rows').
 */
@RunWith(AndroidJUnit4::class)
class SideBarForegroundTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var chrome: ChromePalette

  @Test
  fun aContributedPanelsColourlessTextTakesTheSideBarForeground() {
    show { ContributedSidePanel(contributionDrawing("body"), hostOf("")) }

    assertSideBarForeground("body")
  }

  @Test
  fun theTableOfContentsPlaceholderTakesTheSideBarForeground() {
    show { TableOfContentsSidePanel(outline = null) }

    assertSideBarForeground("No headings")
  }

  @Test
  fun theDebuggersNotStoppedLineTakesTheSideBarForeground() {
    show { DebuggerSidePanel(stack = null, onExpand = { emptyList() }, onSelectFrame = {}, onEvaluate = { null }) }

    assertSideBarForeground("Not stopped")
  }

  @Test
  fun aRunningSectionsTitleTakesTheSideBarForeground() {
    val sessions = FakeKernelSessions(KernelSessionRegistry())
    val terminals = FakeTerminalGateway()
    val viewModel = RunningPanelViewModel(sessions, terminals)
    show {
      RunningSidePanel(
        kernelSessions = sessions,
        terminalGateway = terminals,
        openTabs = emptyList(),
        onActivateTab = {},
        onCloseTab = {},
        saveAllEnabled = false,
        onSaveAllTabs = {},
        onOpenSession = {},
        onOpenTerminal = {},
        viewModel = viewModel,
      )
    }

    assertSideBarForeground("Open Tabs")
  }

  @Test
  fun aSearchResultsFileNameTakesTheSideBarForeground() {
    show {
      val workspace = FakeWorkspaceRepository()
      val replace = WorkspaceReplace(OpenDocuments(), FakeNotebookRepository(), workspace)
      val searcher = FakeWorkspaceSearcher(mapOf("notes.py" to "cat"))
      SearchSidePanel(searcher = searcher, replace = replace, onOpenMatch = { _, _ -> })
    }
    compose.onNodeWithContentDescription(SEARCH_WORKSPACE_BOX).performTextInput("cat")
    compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText("notes.py")).fetchSemanticsNodes().isNotEmpty() }

    assertSideBarForeground("notes.py")
  }

  @Test
  fun theEnvironmentsEmptyStateTakesTheSideBarForeground() {
    val environments = FakeSpaceEnvironments()
    val creation = FakeSpaceEnvironmentCreation()
    val registration = FakeSpaceKernelRegistration()
    val workspace = FakeWorkspaceRepository()
    val viewModel = EnvironmentsPanelViewModel(BASE_SPACE.id, environments, creation, registration, workspace)
    show {
      EnvironmentsSidePanel(
        spaceEnvironments = environments,
        spaceEnvironmentCreation = creation,
        spaceKernelRegistration = registration,
        workspaceRepository = workspace,
        spaceId = BASE_SPACE.id,
        viewModel = viewModel,
      )
    }
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodes(hasText(NO_ENVIRONMENTS)).fetchSemanticsNodes().isNotEmpty()
    }

    assertSideBarForeground(NO_ENVIRONMENTS)
  }

  private fun assertSideBarForeground(text: String) {
    val drawn = compose.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style.color
    assertEquals(chrome.sideBarForeground, drawn)
  }

  private fun show(panel: @Composable () -> Unit) = compose.setContent {
    JupydroidTheme(darkTheme = true) {
      ChromeCapture { chrome = it }
      panel()
    }
  }

  @Composable
  private fun ChromeCapture(onPalette: (ChromePalette) -> Unit) = onPalette(chromePalette)

  private fun contributionDrawing(text: String) = SidePanelContribution(
    id = "colour-probe",
    label = "Probe",
    icon = Icons.Filled.Close,
    content = { Text(text) },
  )

  private fun hostOf(browsedDirectory: String) = SidePanelHost(
    browsedDirectory,
    server = null,
    spaceId = "base",
    RichTextMarkdownRenderer,
    RecordingNotebookCells(available = false),
    RecordingWorkspaceFileOpener(),
  )

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
