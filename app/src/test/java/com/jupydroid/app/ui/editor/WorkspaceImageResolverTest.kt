package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A local image source read from the workspace, resolved against the notebook's directory as
 * `RenderMimeRegistry.UrlResolver` resolves it (jupyterlab@7f0e690 packages/rendermime/src/registry.ts),
 * and every source that is not local passed on to the resolver around it.
 */
class WorkspaceImageResolverTest {
  private val outer = SourceRecordingImageResolver()
  private val workspace = FakeWorkspaceRepository()
  private val resolver = WorkspaceImageResolver("reports/nb.ipynb", workspace, outer)

  @Test
  fun aPathIsReadBesideTheNotebook() = runTest {
    workspace.writeBytes("reports/img/plot.png", PLOT)

    assertArrayEquals(PLOT, resolver.imageBytes("img/plot.png"))
  }

  @Test
  fun aParentPathClimbsOutOfTheNotebooksDirectory() = runTest {
    workspace.writeBytes("shared/plot.png", PLOT)

    assertArrayEquals(PLOT, resolver.imageBytes("../shared/plot.png"))
  }

  /** The contents API is asked for the decoded path, as `UrlResolver.resolveUrl` decodes it. */
  @Test
  fun anEscapedPathFindsTheFile() = runTest {
    workspace.writeBytes("reports/my plot.png", PLOT)

    assertArrayEquals(PLOT, resolver.imageBytes("my%20plot.png"))
  }

  /** A path that names nothing is the workspace's to answer, and its answer is nothing: the alt draws. */
  @Test
  fun aMissingFileIsNothingAndGoesNoFurther() = runTest {
    assertNull(resolver.imageBytes("img/gone.png"))
    assertEquals(emptyList<String>(), outer.asked)
  }

  @Test
  fun aSourceThatIsNotLocalGoesToTheResolverAroundIt() = runTest {
    val notLocal = listOf("https://jupyter.org/logo.png", "attachment:chart.png", "/etc/plot.png", "../../plot.png")

    notLocal.forEach { assertNull(resolver.imageBytes(it)) }
    assertEquals(notLocal, outer.asked)
  }

  private companion object {
    val PLOT = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
  }
}
