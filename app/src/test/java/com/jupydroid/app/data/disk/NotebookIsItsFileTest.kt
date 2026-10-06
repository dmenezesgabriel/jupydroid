package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.BASE_SPACE
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The notebook's name **is** its file, which is JupyterLab's model.
 *
 * Until now the name lived in `metadata.jupydroid.title` while the file was a UUID, so the
 * workspace the kernel shares - and the explorer built over it - would show
 * `9f3a1c02-….ipynb` for a notebook called "Analysis of sales". A rename wrote the JSON and left
 * the file alone, which is the "title inside, name outside" split this closed.
 *
 * Renaming and overwriting themselves moved to
 * [com.jupydroid.app.domain.port.WorkspaceRepository]: the id is a path, so the tree's own port
 * owns every file's acts and announces them on `changes` (ADR-0070).
 */
class NotebookIsItsFileTest {
  @get:Rule val temp = TemporaryFolder()

  private val repository by lazy { DiskNotebookRepository(temp.root) }

  @Test
  fun creatingANotebookNamesTheFileAfterIt() = runTest {
    repository.createNotebook("Analysis of sales", BASE_SPACE.kernels.single())

    assertTrue("Analysis of sales.ipynb" in temp.root.list()!!.toList())
  }

  /** The id is the *path*, suffix and all - `Contents.IModel.path`, which is what the browser lists. */
  @Test
  fun theIdIsTheFileName() = runTest {
    val notebook = repository.createNotebook("Analysis of sales", BASE_SPACE.kernels.single())

    assertEquals("Analysis of sales.ipynb", notebook.id)
    assertEquals("Analysis of sales.ipynb", notebook.title)
  }

  /** Creating with a name already taken gets a suffix: the user asked for a new notebook. */
  @Test
  fun creatingWithATakenNameIsSuffixed() = runTest {
    repository.createNotebook("Untitled", BASE_SPACE.kernels.single())

    val second = repository.createNotebook("Untitled", BASE_SPACE.kernels.single())

    assertEquals("Untitled1.ipynb", second.id)
    assertTrue(File(temp.root, "Untitled.ipynb").exists())
  }
}
