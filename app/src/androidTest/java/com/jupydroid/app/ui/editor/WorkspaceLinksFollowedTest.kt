package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * A workspace link in a rendered markdown cell reaches the document it names through the handler
 * the editor provides, and a link to nothing says so - the chain a JVM test cannot compose.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceLinksFollowedTest {
  @get:Rule val compose = createComposeRule()

  private val workspace = FakeWorkspaceRepository(
    entriesByPath = mapOf(
      "a" to listOf(WorkspaceEntry("notes.md", "a/notes.md", WorkspaceEntryKind.FILE, Instant.EPOCH)),
    ),
  )
  private val opened = mutableListOf<String>()
  private val outer = RecordingUriHandler()
  private var editorAsked = false

  private fun showCell(source: String) = compose.setContent {
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalUriHandler provides outer) {
      WorkspaceLinksFollowed("a/nb.ipynb", workspace, { opened += it }, {}, snackbar) {
        Column {
          MarkdownCell(rememberTextFieldState(source), source, rendered = true, { editorAsked = true }, {})
          SnackbarHost(snackbar)
        }
      }
    }
  }

  @Test
  fun tappingAWorkspaceLinkOpensTheDocumentAndLeavesTheCellRendered() {
    showCell("[notes](notes.md)")

    compose.onNodeWithText("notes", useUnmergedTree = true).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { opened == listOf("a/notes.md") }
    assertFalse("a link's tap must not open the cell's editor", editorAsked)
  }

  @Test
  fun tappingALinkToNothingSaysWhereItPointed() {
    showCell("[gone](gone.md)")

    compose.onNodeWithText("gone", useUnmergedTree = true).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithText("No file at a/gone.md").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(emptyList<String>(), opened)
  }

  @Test
  fun aWebLinkStillReachesTheHandlerAroundIt() {
    showCell("[Jupyter](https://jupyter.org)")

    compose.onNodeWithText("Jupyter", useUnmergedTree = true).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { outer.opened == listOf("https://jupyter.org") }
  }
}

private const val TIMEOUT_MILLIS = 5_000L
