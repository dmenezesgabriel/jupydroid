package com.jupydroid.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.domain.model.Notebook
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Regression for audit A3: creating a notebook wrote the file but Recents looked unchanged until
 * the app was restarted, while Delete updated it immediately.
 *
 * **The list has to be long enough to scroll or this proves nothing.** The repository,
 * `HomeViewModel`, `HomeScreen` and the whole navigation graph all passed create-then-appear
 * tests on an empty directory while the bug was live: a short list has nothing to anchor to.
 * It only reproduced once there were enough notebooks to fill the screen, which is why this
 * seeds [CROWD] of them.
 */
@RunWith(AndroidJUnit4::class)
class HomeCreateTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun aCreatedNotebookIsVisibleInRecentsWithoutARestart() {
    val repository = DiskNotebookRepository(tempFolder.root)
    runBlocking {
      repeat(CROWD) { index ->
        repository.saveNotebook(
          Notebook("older-$index", "Older $index", "python3", emptyList()),
        )
      }
    }
    compose.setContent { HomeScreen(notebookRepository = repository) }

    compose.onNodeWithText("+").performClick()
    compose.onNodeWithText("New notebook").performClick()

    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithText("Untitled.ipynb").fetchSemanticsNodes().isNotEmpty()
    }
    // Displayed, not merely present: the bug put the row above the viewport, where it is
    // composed but never seen.
    // The suffix is part of the name the row shows, the way the reference lists
    // `Untitled.ipynb` - and the way the rename dialog now offers it for editing.
    compose.onNodeWithText("Untitled.ipynb").assertIsDisplayed()
  }

  private companion object {
    /** Comfortably more rows than a phone screen holds, so the list actually scrolls. */
    const val CROWD = 30
    const val TIMEOUT_MILLIS = 5_000L
  }
}
