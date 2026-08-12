package com.jupydroid.app.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The "New Python file" touch path, on a device: the FAB's menu creates a plain file and the
 * screen reports where it landed, so navigation can open the file editor on exactly that.
 *
 * `HomeViewModelTest` argues that creation succeeds; what it cannot answer is whether the gesture
 * reaches it and whether the created path comes back out of the screen.
 */
@RunWith(AndroidJUnit4::class)
class HomeNewPythonFileTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun newPythonFileCreatesUntitledPyAndReportsItsPath() {
    val opened = mutableListOf<String>()
    compose.setContent {
      HomeScreen(
        notebookRepository = DiskNotebookRepository(tempFolder.root),
        workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
        onOpenFile = { opened += it },
      )
    }

    compose.onNodeWithText("+").performClick()
    compose.onNodeWithText("New Python file").performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { opened.contains("untitled.py") }
    assertEquals(listOf("untitled.py"), opened)
  }

  private companion object {
    /** The request crosses to an I/O dispatcher before the path comes back. */
    const val TIMEOUT_MILLIS = 5_000L
  }
}