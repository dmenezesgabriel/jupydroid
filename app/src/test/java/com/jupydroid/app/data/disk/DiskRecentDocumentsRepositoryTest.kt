package com.jupydroid.app.data.disk

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

  override fun repository(): RecentDocumentsRepository = openStore()

  private fun openStore() = DiskRecentDocumentsRepository(stateDir, workspaceDir)

  private fun givenInWorkspace(vararg paths: String) {
    paths.forEach { path ->
      val file = File(workspaceDir, path)
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
   * in the Contents API. Same reasoning that kept X1a's migration stamp out of the rootfs `home`.
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
}
