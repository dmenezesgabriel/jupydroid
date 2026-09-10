package com.jupydroid.app.ui.fileeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * The plain file editor says where its file lives (P7b) - the trail the notebook has had since
 * P2w-1, on the other document this app opens.
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

  private fun open(path: String) {
    File(temp.root, path).apply { parentFile?.mkdirs() }.writeText("value\n")
    compose.setContent {
      JupyterDroidTheme {
        FileEditorScreen(
          path = path,
          workspaceRepository = DiskWorkspaceRepository(temp.root),
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
   * saying nothing is the trade P2v declined the run gutter over. Inherited from P2w-1 rather than
   * re-decided.
   */
  @Test
  fun aFileAtTheRootDrawsNoTrailAtAll() {
    open("data.csv")

    compose.onNodeWithText(ROOT).assertDoesNotExist()
  }

  /** The document's own name is never a segment: it is on the bar above. See `ancestorCrumbsFor`. */
  @Test
  fun aNestedFileSaysWhichFolderItLivesIn() {
    open("$FOLDER/data.csv")

    compose.onNodeWithText(ROOT).assertIsDisplayed()
    compose.onNodeWithText(FOLDER).assertIsDisplayed()
  }

  /**
   * **Under the bar carrying the name**, which is where `BreadcrumbsControl` sits relative to a tab
   * title. This screen's single row *is* its name row, where the notebook has a title strip above
   * its actions and puts the trail between the two.
   */
  @Test
  fun theTrailSitsUnderTheRowCarryingTheName() {
    open("$FOLDER/data.csv")

    val name = compose.onNodeWithText("data.csv").getUnclippedBoundsInRoot()
    val back = compose.onNodeWithContentDescription(BACK).getUnclippedBoundsInRoot()
    val crumb = compose.onNodeWithText(FOLDER).getUnclippedBoundsInRoot()

    assertTrue("the crumb at $crumb is not below the name at $name", crumb.top >= name.bottom)
    assertTrue("the crumb at $crumb is not below the back arrow at $back", crumb.top >= back.bottom)
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

/** The same folder `p2w-1-breadcrumb`'s fixtures use, so the two trails are read the same way. */
private const val FOLDER = "reports"

/** `FileEditorScreen`'s own back arrow, labelled apart from the notebook's on purpose. */
private const val BACK = "Back to files"

/** Long enough for a file read off disk, short enough to fail. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L
