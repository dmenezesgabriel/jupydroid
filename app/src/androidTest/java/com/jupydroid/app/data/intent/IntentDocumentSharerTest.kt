package com.jupydroid.app.data.intent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.data.space.notebookWorkspace
import com.jupydroid.app.data.space.notebookWorkspaceIn
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.data.space.spacesDir
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Everything that can go wrong with sharing goes wrong in the framework's configuration - an
 * authority that does not match the manifest, or a `<files-path>` that does not cover the
 * workspace - and none of it is reachable from a JVM test. `getUriForFile` throws for a file under
 * no configured root, and `openInputStream` is what says the provider will actually serve it.
 *
 * **The driver cannot ask this.** `adb shell content read` runs as the shell user with no URI
 * grant, and the provider is `exported="false"` - so it is denied, correctly, whether the
 * configuration is right or wrong.
 */
@RunWith(AndroidJUnit4::class)
class IntentDocumentSharerTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val workspace = notebookWorkspace(context)
  private val sharer = IntentDocumentSharer(context, workspace)
  private val written = mutableListOf<File>()

  @After
  fun removeWhatWasWritten() {
    written.forEach { it.deleteRecursively() }
  }

  @Test
  fun servesAnExportedFileToWhoeverIsHandedItsUri() {
    val exported = workspaceFile("sharer-test.html", "<html>the export</html>")

    val read = context.contentResolver.openInputStream(sharer.uriFor(exported.name))
      ?.use { it.readBytes().decodeToString() }

    assertEquals("<html>the export</html>", read)
  }

  /** An export lands beside its notebook, and a notebook can be in a folder. */
  @Test
  fun servesAFileInAFolderInsideTheWorkspace() {
    File(workspace, "sharer-reports").mkdirs()
    val exported = workspaceFile("sharer-reports/deep.html", "<html>deep</html>")
    written += File(workspace, "sharer-reports")

    val uri = sharer.uriFor("sharer-reports/deep.html")

    assertEquals(
      "<html>deep</html>",
      context.contentResolver.openInputStream(uri)?.use {
        it.readBytes().decodeToString()
      },
    )
    assertEquals(exported.name, uri.lastPathSegment?.substringAfterLast('/'))
  }

  /** The authority the manifest declares, since a mismatch fails only at the moment of sharing. */
  @Test
  fun usesTheAuthorityTheManifestDeclares() {
    val exported = workspaceFile("sharer-authority.html", "x")

    assertEquals("${context.packageName}.exports", sharer.uriFor(exported.name).authority)
  }

  /**
   * The rootfs, `recent-documents.json`, `editor-settings.json` and the server's token all live
   * above the workspace, and the provider's root is scoped so that none of them is addressable.
   */
  @Test
  fun refusesAFileOutsideTheWorkspace() {
    val refused = runCatching { sharer.uriFor("../../../../recent-documents.json") }

    assertTrue("expected a refusal, got $refused", refused.exceptionOrNull() is IllegalArgumentException)
  }

  /**
   * The provider covers every Space's directory, since a Space's id is chosen at runtime and a
   * `<files-path>` cannot hold a wildcard. So the sharer is what refuses a path that leaves its
   * own workspace and lands in the Space's rootfs, still under the provider's root.
   */
  @Test
  fun refusesAPathIntoTheSpacesOwnRootfs() {
    val refused = runCatching { sharer.uriFor("../../etc/passwd") }

    assertTrue("expected a refusal, got $refused", refused.exceptionOrNull() is IllegalArgumentException)
  }

  /** A Space other than base shares out of its own `home/jupydroid`. */
  @Test
  fun servesAFileFromAnotherSpacesWorkspace() {
    val thesisWorkspace = notebookWorkspaceIn(spaceDirIn(spacesDir(context), "share-test-space"))
    written += spaceDirIn(spacesDir(context), "share-test-space")
    File(thesisWorkspace, "thesis.html").apply { parentFile?.mkdirs() }.writeText("<html>thesis</html>")

    val uri = IntentDocumentSharer(context, thesisWorkspace).uriFor("thesis.html")

    assertEquals(
      "<html>thesis</html>",
      context.contentResolver.openInputStream(uri)?.use {
        it.readBytes().decodeToString()
      },
    )
  }

  private fun workspaceFile(path: String, content: String): File = File(workspace, path).also {
    it.parentFile?.mkdirs()
    it.writeText(content)
    written += it
  }
}
