package com.jupydroid.app.data.space

import androidx.test.platform.app.InstrumentationRegistry

/**
 * **One `jupyter_server` for the whole instrumentation run**, shared by every device test that
 * needs a Space.
 *
 * Not a convenience. Instrumentation runs every test class in one process, and a second
 * [JupyterServerLauncher] would start a second server against the *same* `run/` directory - where
 * `JupyterRuntimeFile.clear` deletes every `jpserver-*.json` it finds and `read` takes the first by
 * name. Two servers there means one wiping the other's file and either of them reading the other's
 * port; they would also truncate the same `jupyter_server.log`, which is the log every Space
 * investigation reads.
 *
 * That is the process-wide version of the same rule [BaseSpaceServer] states for the app -
 * `JupyterServerLauncher.start` begins with `stop()`, so one object owns the start - and the app
 * keeps it by building the container once per process ([com.jupydroid.app.JupydroidApplication]).
 * A test suite has no such composition root, so this is it.
 *
 * ```
 * val terminals: TerminalGateway by lazy { baseSpaceTerminalGateway(BaseSpaceUnderTest.server) }
 * ```
 */
object BaseSpaceUnderTest {
  /** Null where no Space can run at all, which is what the `Assume` in each test class reads. */
  val server: BaseSpaceServer? by lazy {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    baseSpaceServer(context, baseSpaceServerLauncher(context))
  }

  /** The server, or a failure naming the device - for tests that have already assumed a Space. */
  fun require(): BaseSpaceServer = requireNotNull(server) { "no Space runs on this device" }
}
