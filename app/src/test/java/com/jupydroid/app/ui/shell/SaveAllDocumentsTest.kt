package com.jupydroid.app.ui.shell

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.Editor
import com.jupydroid.app.FileEditor
import com.jupydroid.app.Launcher
import com.jupydroid.app.OpenDocuments
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Save All: every dirty open document written in strip order, and the first one the disk refuses
 * brought to the top - only the top tab composes, so that is the one place its question can draw.
 */
class SaveAllDocumentsTest {
  private val saveLog = mutableListOf<String>()
  private val revealed = mutableListOf<NavKey>()
  private val openDocuments = OpenDocuments()

  private fun open(path: String, dirty: Boolean, refuses: Boolean = false) =
    openDocuments.opened(ScriptedSaveDocument(path, dirty, saveLog, refuses))

  @Test
  fun everyDirtyDocumentIsSavedInStripOrderAndNoneIsRevealed() = runTest {
    open("b.py", dirty = true)
    open("a.ipynb", dirty = true)

    saveAllOpenDocuments(listOf(FileEditor("b.py"), Launcher, Editor("a.ipynb")), openDocuments, revealed::add)

    assertEquals(listOf("b.py", "a.ipynb"), saveLog)
    assertTrue(revealed.isEmpty())
  }

  /** A clean document has nothing to write, and writing it would move its mtime for nothing. */
  @Test
  fun aCleanDocumentIsNotWritten() = runTest {
    open("clean.py", dirty = false)
    open("dirty.py", dirty = true)

    saveAllOpenDocuments(listOf(FileEditor("clean.py"), FileEditor("dirty.py")), openDocuments, revealed::add)

    assertEquals(listOf("dirty.py"), saveLog)
  }

  @Test
  fun theFirstRefusedSaveRevealsItsTabAndStopsTheSweep() = runTest {
    open("first.py", dirty = true)
    open("refused.py", dirty = true, refuses = true)
    open("after.py", dirty = true)

    saveAllOpenDocuments(
      listOf(FileEditor("first.py"), FileEditor("refused.py"), FileEditor("after.py")),
      openDocuments,
      revealed::add,
    )

    assertEquals(listOf("first.py", "refused.py"), saveLog)
    assertEquals(listOf<NavKey>(FileEditor("refused.py")), revealed)
  }

  /** A tab whose document has not registered - never composed since launch - owes nothing. */
  @Test
  fun aTabWithNoOpenDocumentIsPassedOver() = runTest {
    open("open.py", dirty = true)

    saveAllOpenDocuments(listOf(FileEditor("unregistered.py"), FileEditor("open.py")), openDocuments, revealed::add)

    assertEquals(listOf("open.py"), saveLog)
  }
}
