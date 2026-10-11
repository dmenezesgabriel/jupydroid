package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WorkspaceLinkUriHandlerTest {
  private val workspace = FakeWorkspaceRepository(
    entriesByPath = mapOf(
      "" to listOf(entry("a", WorkspaceEntryKind.DIRECTORY)),
      "a" to listOf(entry("a/docs", WorkspaceEntryKind.DIRECTORY), entry("a/notes.md", WorkspaceEntryKind.FILE)),
    ),
  )
  private val followed = mutableListOf<String>()

  private suspend fun follow(path: String) = workspace.followWorkspaceLink(
    path,
    openDocument = { followed += "open $it" },
    goToFolder = { followed += "browse $it" },
    reportMissing = { followed += "missing $it" },
  )

  @Test
  fun aFileOpensAsADocument() = runTest {
    follow("a/notes.md")

    assertEquals(listOf("open a/notes.md"), followed)
  }

  @Test
  fun aDirectoryIsBrowsed() = runTest {
    follow("a/docs")
    follow("")

    assertEquals(listOf("browse a/docs", "browse "), followed)
  }

  @Test
  fun aPathWithNothingThereIsReportedMissing() = runTest {
    follow("a/gone.md")
    follow("b/gone.md")

    assertEquals(listOf("missing a/gone.md", "missing b/gone.md"), followed)
  }

  /** The real repository refuses to list a directory that is not there rather than listing nothing. */
  @Test
  fun aRefusedListingIsReportedMissing() = runTest {
    RefusingWorkspaceRepository().followWorkspaceLink("b/gone.md", {}, {}) { followed += "missing $it" }

    assertEquals(listOf("missing b/gone.md"), followed)
  }

  @Test
  fun aWorkspaceLinkIsResolvedAgainstTheNotebookAndFollowed() {
    val outer = RecordingUriHandler()
    WorkspaceLinkUriHandler("a/nb.ipynb", outer) { followed += it }.openUri("../a/notes.md")

    assertEquals(listOf("a/notes.md"), followed)
    assertEquals(emptyList<String>(), outer.opened)
  }

  /** A traceback's path is the kernel's, rooted at the workspace, not at the notebook's folder. */
  @Test
  fun aPathTheKernelPrintedIsFollowedFromTheWorkspaceRoot() {
    val outer = RecordingUriHandler()
    WorkspaceLinkUriHandler("a/nb.ipynb", outer) { followed += it }.openUri("/home/jupydroid/mod.py")

    assertEquals(listOf("mod.py"), followed)
    assertEquals(emptyList<String>(), outer.opened)
  }

  @Test
  fun anythingElseGoesToTheHandlerAroundIt() {
    val outer = RecordingUriHandler()
    WorkspaceLinkUriHandler("a/nb.ipynb", outer) { followed += it }.openUri("https://jupyter.org")

    assertEquals(listOf("https://jupyter.org"), outer.opened)
    assertEquals(emptyList<String>(), followed)
  }

  @Test
  fun aMissNamesThePathTheLinkResolvedTo() {
    assertEquals("No file at a/gone.md", missingLinkMessage("a/gone.md"))
  }

  private fun entry(path: String, kind: WorkspaceEntryKind) =
    WorkspaceEntry(path.substringAfterLast('/'), path, kind, Instant.EPOCH)
}

private class RefusingWorkspaceRepository : WorkspaceRepository by FakeWorkspaceRepository() {
  override suspend fun list(path: String): List<WorkspaceEntry> =
    throw IllegalArgumentException("'$path' is not a directory in the workspace")
}
