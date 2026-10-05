package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.TerminalGateway
import java.io.File

/**
 * Wires [BaseSpaceInstaller] to the Android app's own directories.
 *
 * Kept apart from the installer so the installer stays a plain JVM class that unit
 * tests can drive against a temp folder, with no Context in sight.
 */
fun baseSpaceInstaller(context: Context, abi: SpaceAbi = deviceAbi()): BaseSpaceInstaller = BaseSpaceInstaller(
  archive = { context.assets.open("spaces/base/rootfs-${abi.androidAbi}.tzst") },
  baseDir = baseSpaceDir(context),
)

/**
 * The bundled image a create copies into a new Space, wired to the app's assets.
 *
 * [deviceAbi] is resolved inside the lambda rather than here, so on an ABI with no rootfs
 * (x86_64) the refusal lands when a Space is *created* - as the act's on-screen failure - and
 * not while the composition root is being built.
 */
fun spaceImageInstaller(context: Context): SpaceImageInstaller = SpaceImageInstaller(
  archive = ArchiveSource { context.assets.open("spaces/base/rootfs-${deviceAbi().androidAbi}.tzst") },
)

fun baseSpaceDir(context: Context): File = spaceDirIn(spacesDir(context), BASE_SPACE.id)

/**
 * Where notebooks live: the kernel's `HOME`, which is also the server's root and the repository's.
 *
 * See [notebookWorkspaceIn] for why one definition rather than three.
 */
fun notebookWorkspace(context: Context): File = notebookWorkspaceIn(baseSpaceDir(context))

/** Runs jupyter_server in whichever Space `start` is handed, on loopback, under proot. */
fun spaceServerLauncher(context: Context): JupyterServerLauncher = JupyterServerLauncher(
  runtimeFor = { space -> spaceRuntime(context, space) },
  dirsFor = { space -> spaceDirs(context, space) },
)

/**
 * The base Space's server, brought up on first use - or null where no Space can run.
 *
 * Null rather than a throwing object, so the caller decides: on an x86_64 emulator the runtime
 * cannot work at all and the UI is better served by the
 * fakes than by controls that always fail.
 *
 * **One per process, and shared by every client of the Space**, because
 * `JupyterServerLauncher.start` begins with `stop()` - see [BaseSpaceServer]. [launcher] is the
 * caller's for the same reason from the other side: the idle backstop stops what this starts.
 */
fun baseSpaceServer(context: Context, launcher: JupyterServerLauncher): BaseSpaceServer? {
  if (runCatching { deviceAbi() }.isFailure) return null
  return BaseSpaceServer(
    installSpace = { baseSpaceInstaller(context).installIfNeeded() },
    launcher = launcher,
    log = LogcatSpaceLifecycleLog,
  )
}

/**
 * `pylsp` out of a Space's own image as the app's [LanguageServerPort], over stdio - one process
 * per Space, each in that Space's own environment and workspace ([startPylsp]).
 *
 * The console script execs under proot through its `#!` line ([SpaceRuntime]'s
 * `execProcessBuilder`, which must not prefix the interpreter), no `jupyter_server` in between.
 *
 * Null where no Space can run, the same guard [baseSpaceServer] keeps. The process is lazier
 * than the server's own: `installSpace` runs on the port's *first use*, so an app that only
 * browses notebooks never pays for a Python runtime it did not ask for.
 */
fun baseSpaceLanguageServerPort(context: Context): LanguageServerPort? {
  if (runCatching { deviceAbi() }.isFailure) return null
  return StdioLanguageServer(
    installSpace = { baseSpaceInstaller(context).installIfNeeded() },
    spawn = { space, _ ->
      startPylsp(spaceRuntime(context, space), spaceDirs(context, space))
    },
    log = LogcatSpaceLifecycleLog,
  )
}

/** The exporter backed by the nbconvert endpoints of [server]'s `jupyter_server`. */
fun baseSpaceNotebookExporter(server: BaseSpaceServer): NotebookExporter =
  BaseSpaceNotebookExporter(server) { OkHttpNotebookExporter(it) }

/** The terminals of [server]'s `jupyter_server`, over one socket per attached shell. */
fun baseSpaceTerminalGateway(server: BaseSpaceServer): TerminalGateway = BaseSpaceTerminalGateway(
  server = server,
  gatewayFor = { connection ->
    RealTerminalGateway(
      opener = OkHttpTerminalSocketOpener(connection),
      // The lifecycle gets the log for the one thing it reports rather than raises: a terminal that
      // was already gone when we asked to end it, which on device is the only account of it.
      lifecycle = OkHttpTerminalLifecycle(connection, log = LogcatSpaceLifecycleLog),
      // The same tag the rest of the runtime reports under, so a shell that shows nothing is one
      // filter to read: `adb logcat -s JupydroidSpace`. See [TerminalLog].
      log = LogcatSpaceLifecycleLog,
    )
  },
)

/**
 * The rootfs must match the ABI Android picked for the APK's native libraries, which
 * is the first supported one - not necessarily the only one the device can run.
 */
private fun deviceAbi(): SpaceAbi = SpaceAbi.of(Build.SUPPORTED_ABIS.first())
