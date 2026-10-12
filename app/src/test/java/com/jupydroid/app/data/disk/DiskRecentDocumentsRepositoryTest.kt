package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.MAX_RECENTS
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The disk store owes the port's contract, plus what only a real filesystem can be asked. */
class DiskRecentDocumentsRepositoryTest : RecentDocumentsRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  private val stateDir: File get() = File(temp.root, "state")
  private val workspaceDir: File get() = File(temp.root, "workspace").apply { mkdirs() }
  private val thesisWorkspaceDir: File get() = File(temp.root, "thesis-workspace").apply { mkdirs() }

  override fun repository(): RecentDocumentsRepository = openStore()

  /** A fresh [DiskRecentDocumentsFile] each time, which is this app's version of the next launch. */
  private fun openStore(file: DiskRecentDocumentsFile = DiskRecentDocumentsFile(stateDir)) =
    DiskRecentDocumentsRepository(file, BASE_SPACE.id, workspaceDir)

  private fun openThesis(file: DiskRecentDocumentsFile = DiskRecentDocumentsFile(stateDir)) =
    DiskRecentDocumentsRepository(file, THESIS, thesisWorkspaceDir)

  private fun givenInWorkspace(vararg paths: String) = givenIn(workspaceDir, *paths)

  private fun givenIn(root: File, vararg paths: String) {
    paths.forEach { path ->
      val file = File(root, path)
      if (path.endsWith("/")) file.mkdirs() else file.apply { parentFile?.mkdirs() }.writeText("x")
    }
  }

  /**
   * The list is app state, so it outlives the process that wrote it.
   *
   * The reference persists through `IStateDB` under `docmanager:recents` (`recents.ts:288`) and
   * reloads it in the constructor (`_loadRecents`). A second instance over the same directory is
   * this app's version of the next launch.
   */
  @Test
  fun survivesTheNextLaunch() = runTest {
    givenInWorkspace("kept.ipynb")
    openStore().record(RecentDocument("kept.ipynb", WorkspaceEntryKind.FILE))

    val reopened = openStore().observeRecents().first()

    assertEquals(listOf("kept.ipynb"), reopened.map { it.path })
  }

  /**
   * A record replaces the list whole rather than rewriting it where it sits, so a kill mid-write
   * leaves the previous list rather than half of it (ADR-0089).
   */
  @Test
  fun record_replacesTheFileRatherThanRewritingIt() = runTest {
    givenInWorkspace("first.ipynb", "second.ipynb")
    openStore().record(RecentDocument("first.ipynb", WorkspaceEntryKind.FILE))
    val taken = File(stateDir, RECENTS_FILE_NAME).linkTakenBeforeTheWrite(temp.root)
    val before = taken.readText()

    openStore().record(RecentDocument("second.ipynb", WorkspaceEntryKind.FILE))

    assertEquals(before, taken.readText())
  }

  /**
   * A path that is no longer there is dropped rather than offered.
   *
   * `_isValid` asks the Contents API for the path and treats a **404** as invalid
   * (`recents.ts:169-181`); `_loadRecents` filters every invalid entry before the list is shown.
   * This workspace is shared with the kernel, so a cell calling `os.remove` is an ordinary way for
   * a recent to go stale - not an edge case.
   */
  @Test
  fun forgetsWhatIsNoLongerThere() = runTest {
    givenInWorkspace("kept.ipynb", "deleted.ipynb")
    openStore().record(RecentDocument("kept.ipynb", WorkspaceEntryKind.FILE))
    openStore().record(RecentDocument("deleted.ipynb", WorkspaceEntryKind.FILE))

    assertTrue(File(workspaceDir, "deleted.ipynb").delete())

    assertEquals(listOf("kept.ipynb"), openStore().observeRecents().first().map { it.path })
  }

  /** A folder recorded and then removed goes the same way a document does. */
  @Test
  fun forgetsADirectoryThatIsGone() = runTest {
    givenInWorkspace("reports/")
    openStore().record(RecentDocument("reports", WorkspaceEntryKind.DIRECTORY))

    assertTrue(File(workspaceDir, "reports").delete())

    assertTrue(openStore().observeRecents().first().isEmpty())
  }

  /**
   * A renamed file leaves a recent pointing at nothing, and re-checking drops it.
   *
   * The reference does not make recents follow a rename - it drops what no longer resolves.
   * `_isValid` treats a 404 as invalid, and `RecentsMenu.onBeforeAttach` re-validates every time
   * the list is about to be shown (`mainmenu-extension/src/recents.ts`), which is why a stale
   * entry is never offered. Ours is always on screen, so the browser re-checks when it refreshes.
   *
   * Found by `rename-in-editor`: renaming from the editor left the old name in Recents, on screen,
   * naming a file that no longer existed.
   */
  @Test
  fun revalidatingDropsAnEntryWhoseFileWasRenamedAway() = runTest {
    givenInWorkspace("before.ipynb")
    val store = openStore()
    store.record(RecentDocument("before.ipynb", WorkspaceEntryKind.FILE))
    assertTrue(File(workspaceDir, "before.ipynb").renameTo(File(workspaceDir, "after.ipynb")))

    store.revalidate()

    assertTrue("the stale entry is still listed", store.observeRecents().first().isEmpty())
  }

  /** Re-checking is not a wipe: what is still on disk stays, and stays in order. */
  @Test
  fun revalidatingKeepsWhatIsStillThere() = runTest {
    givenInWorkspace("kept.ipynb", "gone.ipynb")
    val store = openStore()
    store.record(RecentDocument("kept.ipynb", WorkspaceEntryKind.FILE))
    store.record(RecentDocument("gone.ipynb", WorkspaceEntryKind.FILE))
    assertTrue(File(workspaceDir, "gone.ipynb").delete())

    store.revalidate()

    assertEquals(listOf("kept.ipynb"), store.observeRecents().first().map { it.path })
  }

  /**
   * The store lives outside the workspace, and that is not an implementation detail.
   *
   * The workspace is the kernel's `HOME` and the server's `root_dir`, shared with any other
   * Jupyter client - so a bookkeeping file there would appear in the explorer's own listing and in
   * an `ls` from a cell. The reference keeps recents in `IStateDB`, which is client state, and not
   * in the Contents API. Same reasoning that keeps the migration stamp out of the rootfs `home`.
   */
  @Test
  fun keepsItsBookkeepingOutOfTheWorkspace() = runTest {
    givenInWorkspace("notes.ipynb")

    openStore().record(RecentDocument("notes.ipynb", WorkspaceEntryKind.FILE))

    assertEquals(listOf("notes.ipynb"), workspaceDir.walkTopDown().filter { it.isFile }.map { it.name }.toList())
    assertTrue("the store wrote nothing at all", stateDir.walkTopDown().any { it.isFile })
  }

  /**
   * A store written by something else must not take the app down on launch.
   *
   * Home is the first screen and this is read before it draws; the reference's `_loadRecents`
   * catches its own failure and carries on with an empty list for the same reason.
   */
  @Test
  fun anUnreadableStoreReadsAsEmpty() = runTest {
    stateDir.mkdirs()
    File(stateDir, RECENTS_FILE_NAME).writeText("{ not json")

    assertTrue(openStore().observeRecents().first().isEmpty())
  }

  /** Validation must not quietly empty the list when the workspace itself is missing. */
  @Test
  fun aPathOutsideTheWorkspaceIsRefusedRatherThanCrashing() = runTest {
    givenInWorkspace("notes.ipynb")
    val store = openStore()
    store.record(RecentDocument("notes.ipynb", WorkspaceEntryKind.FILE))

    store.record(RecentDocument("../escaped.ipynb", WorkspaceEntryKind.FILE))

    val paths = openStore().observeRecents().first().map { it.path }
    assertEquals(listOf("notes.ipynb"), paths)
    assertFalse("an escaping path must never be remembered", paths.contains("../escaped.ipynb"))
  }

  /**
   * The file is the record and the flow repeats it - so a refused write announces nothing:
   * publishing before `writeText` would leave the list showing an entry the next launch never
   * kept. A directory standing where the file belongs is a write the filesystem refuses.
   */
  @Test
  fun aRefusedWritePublishesNothing() = runTest {
    givenInWorkspace("kept.ipynb")
    val store = openStore()
    File(stateDir, RECENTS_FILE_NAME).mkdirs()

    val refused = runCatching { store.record(RecentDocument("kept.ipynb", WorkspaceEntryKind.FILE)) }

    assertTrue(refused.exceptionOrNull() is java.io.IOException)
    assertTrue(store.observeRecents().first().isEmpty())
  }

  /**
   * One file holds every Space's recents, and each Space is shown its own: JupyterLab keeps one
   * `docmanager:recents` store and tags each entry with the server root it was opened under
   * (`jupyterlab@7f0e690 packages/docmanager/src/recents.ts`, `addRecent`).
   */
  @Test
  fun eachSpaceSeesOnlyItsOwnRecents() = runTest {
    givenInWorkspace("base.ipynb")
    givenIn(thesisWorkspaceDir, "thesis.ipynb")
    val file = DiskRecentDocumentsFile(stateDir)
    openStore(file).record(RecentDocument("base.ipynb", WorkspaceEntryKind.FILE))
    openThesis(file).record(RecentDocument("thesis.ipynb", WorkspaceEntryKind.FILE))

    assertEquals(listOf("base.ipynb"), openStore().observeRecents().first().map { it.path })
    assertEquals(listOf("thesis.ipynb"), openThesis().observeRecents().first().map { it.path })
  }

  /** Two Spaces open in one process write one file, and neither write may drop the other's entries. */
  @Test
  fun aRecordInOneSpaceKeepsTheOthersEntries() = runTest {
    givenInWorkspace("base.ipynb")
    givenIn(thesisWorkspaceDir, "thesis.ipynb")
    val file = DiskRecentDocumentsFile(stateDir)
    val base = openStore(file)
    val thesis = openThesis(file)

    base.record(RecentDocument("base.ipynb", WorkspaceEntryKind.FILE))
    thesis.record(RecentDocument("thesis.ipynb", WorkspaceEntryKind.FILE))

    assertEquals(listOf("base.ipynb"), openStore().observeRecents().first().map { it.path })
  }

  /** A list written before Spaces were entered carries no tag, and every entry in it was base's. */
  @Test
  fun anUntaggedEntryFromBeforeIsBases() = runTest {
    givenInWorkspace("old.ipynb")
    givenIn(thesisWorkspaceDir, "old.ipynb")
    stateDir.mkdirs()
    File(stateDir, RECENTS_FILE_NAME).writeText("""{"documents":[{"path":"old.ipynb","directory":false}]}""")

    assertEquals(listOf("old.ipynb"), openStore().observeRecents().first().map { it.path })
    assertTrue(openThesis().observeRecents().first().isEmpty())
  }

  /** `docmanager:clear-recents` from inside one Space forgets that Space's list and no other. */
  @Test
  fun clearingOneSpaceLeavesTheOther() = runTest {
    givenInWorkspace("base.ipynb")
    givenIn(thesisWorkspaceDir, "thesis.ipynb")
    val file = DiskRecentDocumentsFile(stateDir)
    openStore(file).record(RecentDocument("base.ipynb", WorkspaceEntryKind.FILE))
    openThesis(file).record(RecentDocument("thesis.ipynb", WorkspaceEntryKind.FILE))

    openThesis(file).clear()

    assertEquals(listOf("base.ipynb"), openStore().observeRecents().first().map { it.path })
  }

  /**
   * A Space validates against its own workspace alone, so it cannot judge another Space's entry -
   * `thesis.ipynb` is not in base's tree, and base's revalidate must leave it.
   */
  @Test
  fun revalidatingOneSpaceLeavesTheOthersEntries() = runTest {
    givenInWorkspace("base.ipynb")
    givenIn(thesisWorkspaceDir, "thesis.ipynb")
    val file = DiskRecentDocumentsFile(stateDir)
    val base = openStore(file)
    base.record(RecentDocument("base.ipynb", WorkspaceEntryKind.FILE))
    openThesis(file).record(RecentDocument("thesis.ipynb", WorkspaceEntryKind.FILE))
    assertTrue(File(workspaceDir, "base.ipynb").delete())

    base.revalidate()

    assertEquals(listOf("thesis.ipynb"), openThesis().observeRecents().first().map { it.path })
  }

  /**
   * The cap is per Space. JupyterLab slices the whole store before it filters by root
   * (`recents.ts`, `_setRecents`), so a busy root crowds a quiet one out of its own list.
   */
  @Test
  fun theCapIsPerSpace() = runTest {
    val names = (1..MAX_RECENTS).map { "n$it.ipynb" }
    givenInWorkspace(*names.toTypedArray())
    givenIn(thesisWorkspaceDir, "thesis.ipynb")
    val file = DiskRecentDocumentsFile(stateDir)
    val base = openStore(file)
    names.forEach { base.record(RecentDocument(it, WorkspaceEntryKind.FILE)) }

    openThesis(file).record(RecentDocument("thesis.ipynb", WorkspaceEntryKind.FILE))

    assertEquals(MAX_RECENTS, openStore().observeRecents().first().size)
  }

  private companion object {
    const val THESIS = "space-1"
  }
}
