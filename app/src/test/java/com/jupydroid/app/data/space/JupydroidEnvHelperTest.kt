package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * The helper's install: the script bytes land at `usr/local/bin/jupydroid-env` executable, a
 * rewrite replaces them whole, and an asset the APK does not ship is a loud failure rather than
 * an empty helper.
 */
class JupydroidEnvHelperTest {
  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun install_writesTheScriptExecutableWhereTheTerminalFindsIt() = runTest {
    val spaceDir = tempFolder.newFolder("space-1")

    JupydroidEnvHelper.install(spaceDir, openAsset = { SCRIPT.toByteArray().inputStream() })

    val helper = File(spaceDir, "usr/local/bin/jupydroid-env")
    assertEquals(SCRIPT, helper.readText())
    assertEquals(HELPER_MODE, Files.getPosixFilePermissions(helper.toPath()))
  }

  /** Every Space start rewrites the helper, so an APK update reaches Spaces already extracted. */
  @Test
  fun install_rewritesAnOlderScript() = runTest {
    val spaceDir = tempFolder.newFolder("space-1")
    JupydroidEnvHelper.install(spaceDir, openAsset = { "old script".toByteArray().inputStream() })

    JupydroidEnvHelper.install(spaceDir, openAsset = { SCRIPT.toByteArray().inputStream() })

    val helper = File(spaceDir, "usr/local/bin/jupydroid-env")
    assertEquals(SCRIPT, helper.readText())
    assertEquals(HELPER_MODE, Files.getPosixFilePermissions(helper.toPath()))
  }

  @Test
  fun install_refusesAnAssetTheApkDoesNotShip() = runTest {
    val spaceDir = tempFolder.newFolder("space-1")

    val failure = runCatching {
      JupydroidEnvHelper.install(spaceDir, openAsset = { throw FileNotFoundException("gone") })
    }.exceptionOrNull()

    assertTrue("expected IllegalStateException, was $failure", failure is IllegalStateException)
    assertTrue(
      "message must name the asset path, was '${failure?.message}'",
      failure?.message?.contains("space-tools/jupydroid-env.sh") == true,
    )
  }

  private companion object {
    const val SCRIPT = "#!/bin/sh\necho hi\n"

    val HELPER_MODE = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE,
    )
  }
}
