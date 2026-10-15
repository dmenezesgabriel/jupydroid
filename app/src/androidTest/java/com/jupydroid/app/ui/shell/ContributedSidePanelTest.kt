package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelHost
import com.jupydroid.extensions.git.GitExtension
import com.jupydroid.extensions.git.data.fake.FakeGitStatusReader
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

  private val sourceControl = GitExtension(
    readerFor = { FakeGitStatusReader(repositories = mapOf("demo" to RepositoryStatus.Repository("master"))) },
  ).sidePanels.single()

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

  private fun show(browsedDirectory: String) {
    compose.setContent {
      JupyterDroidTheme {
        ContributedSidePanel(contribution = sourceControl, host = SidePanelHost(browsedDirectory, server = null))
      }
    }
  }
}
