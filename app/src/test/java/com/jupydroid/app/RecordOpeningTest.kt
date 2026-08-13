package com.jupydroid.app

import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Opening a document is what puts it in Recents - one seam, not one per screen.
 *
 * The reference records from a single place too: `_recordAsRecentlyOpened` hangs off the widget
 * manager's `activate-request` hook (`packages/docmanager/src/widgetmanager.ts:268-285`), not off
 * the file browser or the launcher. Ours hangs off navigation, which every opener already goes
 * through - Home's rows, the explorer's rows, and the VIEW-intent import.
 */
class RecordOpeningTest {
  @Test
  fun openingADocumentRecordsIt() = runTest {
    val recents = FakeRecentDocumentsRepository()

    recents.recordOpening("notes.ipynb")

    assertEquals(listOf("notes.ipynb"), recents.observeRecents().first().map { it.path })
  }

  /** Both halves of the rule land, and the folder is remembered as a folder. */
  @Test
  fun openingInsideAFolderRecordsTheFolderToo() = runTest {
    val recents = FakeRecentDocumentsRepository()

    recents.recordOpening("reports/summary.ipynb")

    val recorded = recents.observeRecents().first().associate { it.path to it.kind }
    assertEquals(WorkspaceEntryKind.FILE, recorded["reports/summary.ipynb"])
    assertEquals(WorkspaceEntryKind.DIRECTORY, recorded["reports"])
  }

  /** A plain file is a document like any other - X2c gave `.py` an editor, so it is openable. */
  @Test
  fun aPythonFileIsRecordedLikeANotebook() = runTest {
    val recents = FakeRecentDocumentsRepository()

    recents.recordOpening("scripts/run.py")

    assertEquals(
      listOf("scripts", "scripts/run.py"),
      recents.observeRecents().first().map { it.path }.sorted(),
    )
  }

  /** Re-opening what is already open still counts as an open, the way `activate-request` does. */
  @Test
  fun openingTheSameThingTwiceLeavesOneEntry() = runTest {
    val recents = FakeRecentDocumentsRepository()

    recents.recordOpening("notes.ipynb")
    recents.recordOpening("notes.ipynb")

    assertEquals(1, recents.observeRecents().first().size)
  }
}
