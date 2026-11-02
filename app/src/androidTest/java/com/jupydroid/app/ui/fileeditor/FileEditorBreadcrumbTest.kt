package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The plain file editor says where its file lives - the trail the notebook already has, on the
 * other document this app opens.
 *
 * Instrumented rather than JVM for the reason [com.jupydroid.app.ui.editor.NotebookBreadcrumbTest]
 * is: every question here is about *layout* - whether a row is drawn at all and which row it is
 * under. The segmenting itself is `PathCrumbsTest`, and this screen reuses `ancestorCrumbsFor`
 * rather than owning a second copy of it.
 */
@RunWith(AndroidJUnit4::class)
class FileEditorBreadcrumbTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /** Every folder a crumb reported, in the order it was tapped - the *path*, never the label. */
  private val browsedTo = mutableListOf<String>()

  /**
   * The file editor draws **no** dismiss control and no bar for one to live on, at any width -
   * leaving the document is the tab strip's own act: the ✕ beside a browser, the destination-named
   * arrow below one pane (`MainAreaTabStrip`'s). The trail still sits where `BreadcrumbsControl`
   * sits - under where the bar was - so the document still says which folder it lives in.
   */
  @Test
  fun thereIsNoInPaneDismissAndNoBarForIt() {
    open("reports/data.csv")

    compose.onAllNodesWithContentDescription(BACK, substring = true).assertCountEquals(0)
    compose.onNodeWithText(FOLDER).assertIsDisplayed()
  }

  /**
   * At the root the editor has **no top chrome at all** - the crumb trail is the only thing it
   * could have drawn, and a nested file is the only case it draws.
   */
  @Test
  fun aRootFileDrawsNeitherBarNorTrail() {
    open("data.csv")

    compose.onAllNodesWithContentDescription(BACK, substring = true).assertCountEquals(0)
    compose.onNodeWithText(ROOT).assertDoesNotExist()
    // The document itself is on screen - the reclaimed row went to it, not to a gap.
    compose.onNode(hasSetTextAction()).assertIsDisplayed()
  }

  private fun open(path: String) {
    File(temp.root, path).apply { parentFile?.mkdirs() }.writeText("value\n")
    compose.setContent {
      JupyterDroidTheme {
        FileEditorScreen(
          path = path,
          workspaceRepository = DiskWorkspaceRepository(temp.root),
          workspaceFiles = DiskWorkspaceFiles(temp.root),
          applicationScope = scope,
          editorSettingsRepository = FakeEditorSettingsRepository(),
          onBack = {},
          onGoToFolder = { browsedTo += it },
        )
      }
    }
    compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
      compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /**
   * **Absent at the root, not blank** - `ancestorCrumbsFor` returns empty there, and 48dp spent
   * saying nothing is a trade this app declines everywhere. Inherited from the notebook's trail
   * rather than re-decided.
   */
  @Test
  fun aFileAtTheRootDrawsNoTrailAtAll() {
    open("data.csv")

    compose.onNodeWithText(ROOT).assertDoesNotExist()
  }

  /** The document's own name is never a segment: it is the tab's label. See `ancestorCrumbsFor`. */
  @Test
  fun aNestedFileSaysWhichFolderItLivesIn() {
    open("$FOLDER/data.csv")

    compose.onNodeWithText(ROOT).assertIsDisplayed()
    compose.onNodeWithText(FOLDER).assertIsDisplayed()
  }

  /**
   * **Above the document itself**, which is where `BreadcrumbsControl` sits relative to the text
   * it navigates - the trail is now the only row this screen's top carries, so "under the action
   * row" reads as "over the field" here.
   */
  @Test
  fun theTrailSitsAboveTheDocument() {
    open("$FOLDER/data.csv")

    val crumb = compose.onNodeWithText(FOLDER).getUnclippedBoundsInRoot()
    val field = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()

    assertTrue("the field at $field is not below the crumb at $crumb", field.top >= crumb.bottom)
  }

  /** The root crumb's path is the empty string, which is a target and not "no target". */
  @Test
  fun theRootCrumbBrowsesToTheWorkspaceRoot() {
    open("$FOLDER/data.csv")

    compose.onNodeWithText(ROOT).performClick()

    assertEquals(listOf(""), browsedTo)
  }

  /** A folder segment lands in *that* folder, so the path is what travels and not the label. */
  @Test
  fun aFolderCrumbBrowsesToThatFolder() {
    open("$FOLDER/q1/data.csv")

    compose.onNodeWithText("q1").performClick()

    assertEquals(listOf("$FOLDER/q1"), browsedTo)
  }
}

/** `ROOT_LABEL` - the crumb whose path is the empty string, since a blank target is not tappable. */
private const val ROOT = "Workspace"

/** The same folder `notebook-breadcrumb-trail`'s fixtures use, so the two trails are read the same way. */
private const val FOLDER = "reports"

/** Any dismiss control's label - the strip's destination-named arrows all begin with it. */
private const val BACK = "Back"

/** Long enough for a file read off disk, short enough to fail. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
