package com.jupydroid.app.data.space

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.SpaceSettings
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **A restart keeps the finished run's server log**: before the rotation, `run/jupyter_server.log`
 * died twice over at every start - `SpaceDirs.clearRuntime()` emptied `run/` and the next
 * `redirectOutput` truncated whatever survived (ADR-0128). Now the launcher's start renames the
 * finished run's log into the Space's `server-logs/` archive before the clear, and the new run
 * writes a fresh live log (ADR-0167).
 *
 * Driven through the shipped [spaceServerLauncher] on the installed base Space, so what is proved
 * is the path a crash or a failed restart actually takes: `stop()`, then `start()` again. The
 * finished run's bytes are read after `stop()` has reaped the process, so they are the whole log;
 * the archive has to carry them unchanged, and the live log the second run opens has to be a
 * different file.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL scripts/instrumented-suite-driver.sh \
 *   com.jupydroid.app.data.space.ServerLogRotationDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ServerLogRotationDeviceTest {
  @Before
  fun onlyWhereProotShips() = installBaseWhereProotShips()

  @Test(timeout = 300_000)
  fun aRestartArchivesTheFinishedRunsLog() = runBlocking {
    launcher.start(BASE_SPACE)
    launcher.stop()

    val finishedRunLog = jupyterServerLogFile(dirs.runtime).readBytes()
    assertTrue("the finished run wrote an empty log; nothing to archive", finishedRunLog.isNotEmpty())
    val archivesBefore = archiveNames()

    launcher.start(BASE_SPACE)

    val rotated = (archiveNames() - archivesBefore).single()
    assertTrue("'$rotated' is not a run archive's name", rotated.startsWith("jupyter_server-"))
    val archive = File(dirs.serverLogs, rotated)
    assertArrayEquals("the archive is not the finished run's log", finishedRunLog, archive.readBytes())
    val liveLog = jupyterServerLogFile(dirs.runtime).readBytes()
    Log.i(SPACE_LOG_TAG, "server-log rotation: '$rotated' ${archive.length()} B, live log ${liveLog.size} B")
    assertFalse("the live log still holds the finished run's bytes", liveLog.contentEquals(finishedRunLog))
  }

  private fun archiveNames(): Set<String> = dirs.serverLogs.listFiles().orEmpty()
    .map { it.name }
    .filter { it.startsWith("jupyter_server-") }
    .toSet()

  private companion object {
    val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val dirs get() = spaceDirs(context, BASE_SPACE)

    /** One launcher for the class: `JupyterServerLauncher.start` begins with `stop()`. */
    val launcher by lazy {
      spaceServerLauncher(
        context,
        settingsFor = { SpaceSettings() },
        defaultKernelFor = { DEFAULT_KERNEL_ID },
        nativeLibraryDir = patchedProotLibraryDir(),
      )
    }

    @JvmStatic
    @AfterClass
    fun stopTheServer() {
      runBlocking { launcher.stop() }
    }
  }
}
