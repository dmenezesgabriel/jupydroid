package com.jupydroid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import com.jupydroid.app.ui.launcher.LauncherScreen
import com.jupydroid.app.ui.launcher.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the launcher screen must *show* - the cards, the cwd, the section headers - which no JVM
 * test can answer. Card taps are asserted through the ViewModel's own outcome callback rather
 * than a navigation host: what a tap becomes is `LauncherViewModelTest`'s job.
 */
@RunWith(AndroidJUnit4::class)
class LauncherScreenTest {
  @get:Rule val compose = createComposeRule()

  private fun viewModel() = LauncherViewModel(
    FakeNotebookRepository(),
    FakeWorkspaceFiles(),
    FakeTerminalGateway(),
    FakeKernelGateway(),
    FakeKernelSpecs(),
    BASE_SPACE,
  ) { 1 }

  @Test
  fun showsTheCwdAndEverySectionInRankOrder() {
    compose.setContent {
      LauncherScreen(cwd = "reports", viewModel = viewModel(), onLaunched = {})
    }

    compose.onNodeWithText("reports").assertIsDisplayed()
    compose.onNodeWithText("Notebook").assertIsDisplayed()
    compose.onNodeWithText("Console").assertIsDisplayed()
    compose.onNodeWithText("Other").assertIsDisplayed()
  }

  @Test
  fun leavesTheRootCwdHeadingEmpty() {
    compose.setContent {
      LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {})
    }

    // The reference renders the h3 with `cwd` verbatim - empty at the root. A bare "/" was our
    // own fallback, and it read as a stray breadcrumb.
    assertEquals(0, compose.onAllNodesWithText("/").fetchSemanticsNodes().size)
  }

  @Test
  fun showsKernelCardsAsLetterTiles() {
    compose.setContent {
      LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {})
    }

    // Both kernel cards carry the kernelspec's label - "Python 3" twice - so the sections and
    // their letter tiles are what tell a Notebook card from a Console one. The letter itself is
    // decoration: it must not reach the semantics tree, or TalkBack reads "P Python 3".
    assertEquals(2, compose.onAllNodesWithText("Python 3").fetchSemanticsNodes().size)
    assertEquals(0, compose.onAllNodesWithText("P").fetchSemanticsNodes().size)
    compose.onNodeWithText("Terminal").assertIsDisplayed()
    compose.onNodeWithText("Text File").assertIsDisplayed()
    compose.onNodeWithText("Markdown File").assertIsDisplayed()
    compose.onNodeWithText("Python File").assertIsDisplayed()
  }

  @Test
  fun tappingACardLaunchesItsCommand() {
    val viewModel = viewModel()
    compose.setContent {
      LauncherScreen(cwd = "reports", viewModel = viewModel, onLaunched = {})
    }

    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_TEXT_FILE.name}").performClick()

    compose.waitForIdle()
    // The fake's createFile throws, so the tap's evidence is the surfaced error - the same
    // assertion `aFailedLaunchSurfacesTheError` makes at the ViewModel layer.
    compose.onNodeWithText("Error").assertIsDisplayed()
  }
}
