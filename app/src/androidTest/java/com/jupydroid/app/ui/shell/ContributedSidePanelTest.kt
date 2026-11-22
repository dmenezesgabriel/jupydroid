package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.FailsOnceGitStatusReader
import com.jupydroid.app.NeverAnsweringGitStatusReader
import com.jupydroid.app.RecordingNotebookCells
import com.jupydroid.app.RecordingWorkspaceFileOpener
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.git.GitExtension
import com.jupydroid.extensions.git.data.fake.FakeGitStatusReader
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.RepositoryStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An extension's panel as the shell draws it (ADR-0104): the host's own title bar over the
 * extension's body, and the body told which directory the explorer is showing.
 *
 * The Git extension itself rather than a stand-in, over its fake reader: what is in question is that
 * the host hands the panel what it reads, and the extension is the one thing that reads it.
 */
@RunWith(AndroidJUnit4::class)
class ContributedSidePanelTest {
  @get:Rule val compose = createComposeRule()

  private val workspace =
    FakeGitStatusReader(repositories = mapOf("demo" to RepositoryStatus.Repository("master")))

  @Test
  fun theHostTitlesThePanelWithItsLabel() {
    show(browsedDirectory = "demo")

    compose.onNodeWithText("Source Control").assertIsDisplayed()
  }

  @Test
  fun thePanelReadsTheBrowsedDirectory() {
    show(browsedDirectory = "demo")

    compose.onNodeWithText("master").assertIsDisplayed()
  }

  @Test
  fun aDirectoryOutsideARepositoryIsSaidToBe() {
    show(browsedDirectory = "")

    compose.onNodeWithText("You are not currently in a Git repository.").assertIsDisplayed()
  }

  @Test
  fun whileGitIsBeingReadThePanelSaysSo() {
    show(browsedDirectory = "demo", reader = NeverAnsweringGitStatusReader())

    compose.onNodeWithText("Reading Git status…").assertIsDisplayed()
  }

  @Test
  fun aFailedReadOffersRetryAndRetryReadsAgain() {
    show(browsedDirectory = "demo", reader = FailsOnceGitStatusReader(branch = "master"))

    compose.onNodeWithText("git status of 'demo' failed: connection refused").assertIsDisplayed()
    compose.onNodeWithText("Retry").performClick()
    compose.onNodeWithText("master").assertIsDisplayed()
  }

  @Test
  fun aDirectoryOutsideARepositoryOffersRetry() {
    show(browsedDirectory = "")

    compose.onNodeWithText("Retry").assertIsDisplayed()
  }

  private fun show(browsedDirectory: String, reader: GitStatusReader = workspace) {
    val sourceControl = GitExtension(readerFor = { reader }).sidePanels.single()
    compose.setContent {
      JupyterDroidTheme {
        ContributedSidePanel(
          contribution = sourceControl,
          host = SidePanelHost(
            browsedDirectory,
            server = null,
            spaceId = "base",
            RichTextMarkdownRenderer,
            RecordingNotebookCells(available = false),
            RecordingWorkspaceFileOpener(),
          ),
        )
      }
    }
  }
}
