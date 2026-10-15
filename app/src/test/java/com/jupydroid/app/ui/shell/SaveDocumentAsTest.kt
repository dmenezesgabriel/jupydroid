package com.jupydroid.app.ui.shell

import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * `docmanager:save-as` short of its dialogs: a free path is written, a taken one is asked about
 * before anything is replaced, and a write the disk refuses says why (jupyterlab@7f0e690
 * `packages/docregistry/src/context.ts`, `saveAs`, `_maybeOverWrite`, `_finishSaveAs`).
 */
class SaveDocumentAsTest {
  private val workspace = FakeWorkspaceRepository(
    entriesByPath = mapOf("" to listOf(listed("notes.py"), listed("other.py"))),
    textByPath = mapOf("notes.py" to "saved", "other.py" to "theirs"),
  )

  @Test
  fun aFreePathIsWritten() = runTest {
    val outcome = saveDocumentAs(CopyingOpenDocument("notes.py", "edited", workspace), "copy.py", workspace)

    assertEquals(SaveAsOutcome.Written, outcome)
    assertEquals("edited", workspace.readText("copy.py"))
  }

  /** "File Overwrite?" comes before the write, so a taken file is untouched until the user answers. */
  @Test
  fun aTakenPathIsAskedAboutAndLeftAlone() = runTest {
    val outcome = saveDocumentAs(CopyingOpenDocument("notes.py", "edited", workspace), "other.py", workspace)

    assertEquals(SaveAsOutcome.Taken, outcome)
    assertEquals("theirs", workspace.readText("other.py"))
  }

  /** The dialog's Overwrite: the same save, with the probe already answered. */
  @Test
  fun anOverwriteReplacesTheTakenFile() = runTest {
    val document = CopyingOpenDocument("notes.py", "edited", workspace)

    val outcome = saveDocumentAs(document, "other.py", workspace, overwrite = true)

    assertEquals(SaveAsOutcome.Written, outcome)
    assertEquals("edited", workspace.readText("other.py"))
  }

  /** A folder's listing answers for the folder only: the same name one level down is free. */
  @Test
  fun aNameTakenInAnotherFolderIsFree() = runTest {
    val outcome = saveDocumentAs(CopyingOpenDocument("notes.py", "edited", workspace), "reports/other.py", workspace)

    assertEquals(SaveAsOutcome.Written, outcome)
  }

  @Test
  fun aRefusedWriteSaysWhy() = runTest {
    val document = CopyingOpenDocument("notes.py", "edited", workspace, IOException("no folder 'gone'"))

    val outcome = saveDocumentAs(document, "gone/copy.py", workspace)

    assertEquals(SaveAsOutcome.Refused("no folder 'gone'"), outcome)
  }

  private fun listed(path: String) = WorkspaceEntry(path, path, WorkspaceEntryKind.FILE, Instant.EPOCH)
}
